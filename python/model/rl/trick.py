from typing import Optional

from rl.jass_rules import Card, Suit, RANK_BOURG


class Trick:
    """
    Python equivalent of Plie.java.
    """

    def __init__(self, trump: Suit, card: Optional[Card] = None, player: Optional[int] = None):
        self.highest: Optional[Card] = None
        self.cut: bool = False
        self.owner: Optional[int] = None
        self.cards: list[Card] = []
        self._trump = trump

        if card and player:
            self.cards.append(card)
            self.highest = card
            self.owner = player

    def get_color(self) -> int:
        """Returns the color of the lead card, or -1 if empty."""
        if not self.cards:
            return -1
        return self.cards[0].get_suit()

    def get_highest_rank(self) -> int:
        """Returns the rank of the winning card."""
        if self.highest is None:
            return -1
        return self.highest.get_rank()

    def get_winning_card(self) -> Optional[Card]:
        return self.highest

    def get_winning_index(self) -> int:
        """Returns the index (0-3) of the winning card."""
        if self.highest is None:
            return -1
        return self.cards.index(self.highest)

    def get_score(self) -> int:
        score = sum(c.get_value() for c in self.cards)
        return score * 2 if self._trump == Suit.SPADE else score

    def get_owner(self) -> Optional[int]:
        return self.owner

    def is_cut(self) -> bool:
        return self.cut

    def get_size(self) -> int:
        return len(self.cards)

    def is_empty(self) -> bool:
        return not self.cards

    def is_full(self) -> bool:
        return len(self.cards) == 4

    def get_cards(self) -> list[Card]:
        return self.cards

    def play_card(self, card: Card, player: int, hand: Optional[list[Card]] = None):
        """
        Plays a card into the trick.
        """
        if not self.cards:
            self._take_plie(card, player)
        elif card.get_suit() == self.get_color():
            self._follow(card, player)
        else:
            self._does_not_follow(card, player, hand)

    def can_play(self, card: Card, hand: list[Card]) -> bool:
        """
        Checks if a move is legal. Corresponds to Java canPlay.
        """
        if not self.cards:
            return True
        if card.get_suit() == self.get_color():
            return True

        # Playing Trump (Atout)
        if card.get_suit() == self._trump:
            if not self.cut:
                return True
            if card > self.highest:
                return True

            # Check for under-trumping rules
            has_non_trump = any(c.get_suit() != self._trump for c in hand)
            if has_non_trump:
                return False

            has_higher_trump = any(c > self.highest for c in hand if c.get_suit() == self._trump)
            return not has_higher_trump

        # Not following suit and not playing trump
        has_asked_color = any(c.get_suit() == self.get_color() for c in hand)
        if has_asked_color:
            # Exception: Bourg Sec logic
            return self.get_color() == self._trump and self._has_bourg_sec(hand)

        return True

    def _follow(self, card: Card, player: int):
        if not self.cut and card > self.highest:
            self._take_plie(card, player)
            return
        self.cards.append(card)

    def _does_not_follow(self, card: Card, player: int, hand: list[Card]):
        if card.get_suit() == self._trump:
            self._cut_plie(card, player, hand)
            return

        if hand is not None:
            has_asked_color = any(c.get_suit() == self.get_color() for c in hand)
            if has_asked_color:
                # Check Bourg Sec exception
                if self.get_color() != self._trump or not self._has_bourg_sec(hand):
                    raise RuntimeError("Must follow suit")

        self.cards.append(card)

    def _cut_plie(self, card: Card, player: int, hand: list[Card]):
        if not self.cut:
            self._take_plie(card, player)
            self.cut = True
            return

        if card > self.highest:
            self._take_plie(card, player)
            return

        if hand:
            has_non_atout = any(c.get_suit() != self._trump for c in hand)
            if has_non_atout:
                raise RuntimeError("Cannot under-cut if holding non-trump cards")

            has_higher_atout = any(c > self.highest for c in hand if c.get_suit() == self._trump)
            if has_higher_atout:
                raise RuntimeError("Cannot under-cut if holding higher trump")

        self.cards.append(card)

    def _take_plie(self, card: Card, player: int):
        self.highest = card
        self.owner = player
        self.cards.append(card)

    def _has_bourg_sec(self, hand: list[Card]) -> bool:
        """
        Checks if the hand holds the 'Bourg Sec' (Jack of Trumps and no other trumps).
        """
        trump_cards = [c for c in hand if c.get_suit() == self._trump]
        if len(trump_cards) != 1:
            return False
        return trump_cards[0].get_rank() == RANK_BOURG
