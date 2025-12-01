from typing import Optional

from rl.jass_rules import Card, Suit, RANK_BOURG


class Trick:

    def __init__(self, trump: Suit, card: Optional[Card] = None, player: Optional[int] = None):
        self._highest: Optional[Card] = None
        self._cut: bool = False
        self._owner: Optional[int] = None
        self._cards: list[Card] = []
        self._trump = trump

        if card and player:
            self._cards.append(card)
            self._highest = card
            self._owner = player

    @property
    def lead_suit(self) -> Suit:
        """Returns the color of the lead card, or -1 if empty."""
        if not self._cards:
            return Suit.NONE
        return self._cards[0].suit

    @property
    def highest_rank(self) -> int:
        """Returns the rank of the winning card."""
        if self._highest is None:
            return -1
        return self._highest.rank

    def get_winning_card(self) -> Optional[Card]:
        return self._highest

    def get_winning_index(self) -> int:
        """Returns the index (0-3) of the winning card."""
        if self._highest is None:
            return -1
        return self._cards.index(self._highest)

    @property
    def score(self) -> int:
        score = sum(c.get_value(self._trump) for c in self._cards)
        return score * 2 if self._trump == Suit.SPADE else score

    @property
    def owner(self) -> Optional[int]:
        return self._owner

    @property
    def is_cut(self) -> bool:
        return self._cut

    @property
    def count(self) -> int:
        return len(self._cards)

    @property
    def is_empty(self) -> bool:
        return not self._cards

    @property
    def is_full(self) -> bool:
        return len(self._cards) == 4

    @property
    def cards(self) -> list[Card]:
        return self._cards

    def play_card(self, card: Card, player: int, hand: Optional[list[Card]] = None):
        """
        Plays a card into the trick.
        """
        if not self._cards:
            self._take_plie(card, player)
        elif card.suit == self.lead_suit:
            self._follow(card, player)
        else:
            self._does_not_follow(card, player, hand)

    def can_play(self, card: Card, hand: list[Card]) -> bool:
        """
        Checks if a move is legal. Corresponds to Java canPlay.
        """
        if not self._cards:
            return True
        if card.suit == self.lead_suit:
            return True

        # Playing Trump (Atout)
        if card.suit == self._trump:
            if not self._cut:
                return True
            if Card.compare(card, self._highest, self._trump) > 0:
                return True

            # Check for under-trumping rules
            has_non_trump = any(c.suit != self._trump for c in hand)
            if has_non_trump:
                return False

            has_higher_trump = any(Card.compare(c, self._highest, self._trump) > 0 for c in hand if c.suit == self._trump)
            return not has_higher_trump

        # Not following suit and not playing trump
        has_lead_suit = any(c.suit == self.lead_suit for c in hand)
        if has_lead_suit:
            # Exception: Bourg Sec logic
            return self.lead_suit == self._trump and self._has_bourg_sec(hand)

        return True

    def _follow(self, card: Card, player: int):
        if not self._cut and Card.compare(card, self._highest, self._trump) > 0:
            self._take_plie(card, player)
            return
        self._cards.append(card)

    def _does_not_follow(self, card: Card, player: int, hand: list[Card]):
        if card.suit == self._trump:
            self._cut_plie(card, player, hand)
            return

        if hand is not None:
            has_asked_color = any(c.suit == self.lead_suit for c in hand)
            if has_asked_color:
                # Check Bourg Sec exception
                if self.lead_suit != self._trump or not self._has_bourg_sec(hand):
                    raise RuntimeError("Must follow suit")

        self._cards.append(card)

    def _cut_plie(self, card: Card, player: int, hand: list[Card]):
        if not self._cut:
            self._take_plie(card, player)
            self._cut = True
            return

        if Card.compare(card, self._highest, self._trump) > 0:
            self._take_plie(card, player)
            return

        if hand:
            has_non_atout = any(c.suit != self._trump for c in hand)
            if has_non_atout:
                raise RuntimeError("Cannot under-cut if holding non-trump cards")

            has_higher_atout = any(c > self._highest for c in hand if c.suit == self._trump)
            if has_higher_atout:
                raise RuntimeError("Cannot under-cut if holding higher trump")

        self._cards.append(card)

    def _take_plie(self, card: Card, player: int):
        self._highest = card
        self._owner = player
        self._cards.append(card)

    def _has_bourg_sec(self, hand: list[Card]) -> bool:
        """
        Checks if the hand holds the 'Bourg Sec' (Jack of Trumps and no other trumps).
        """
        trump_cards = [c for c in hand if c.suit == self._trump]
        if len(trump_cards) != 1:
            return False
        return trump_cards[0].rank == RANK_BOURG
