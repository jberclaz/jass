from rl.trick import Trick
from rl.jass_rules import Card, Suit, PlayerPosition, Announcement, RANK_DAME, RANK_ROI, ANNOUNCE_STOECK
from rl.strategy import Strategy


class Player:
    def __init__(self, strategy: Strategy):
        self._strategy = strategy
        self._current_trump = Suit.NONE
        self._hand = []
        self._trick = None
        self._past_tricks = []
        self._scores = [0, 0]
        self._opponent_score = 0
        self._announcements = []
        self._has_stoeck = False

    def set_hand(self, hand: list[Card]):
        self._hand = hand

    def set_trump_suit(self, suit: Suit):
        self._current_trump = suit
        self._compute_announcements()

    def set_announcements(self, announcements):
        for player, a in announcements:
            self._scores[player % 2] += 2 * a.value if self._current_trump == Suit.SPADE else a.value

    def get_announcements(self):
        if len(self._hand) == 8:  # after playing the first card
            return self._announcements
        if self._has_stoeck:
            stoeck = (Card.from_rank_color(RANK_DAME, self._current_trump),
                      Card.from_rank_color(RANK_ROI, self._current_trump))
            if sum(c in stoeck for c in self._hand) == 0:
                self._has_stoeck = False
                return [Announcement(ANNOUNCE_STOECK, stoeck[1])]
        return []

    def played(self, card: Card, player: PlayerPosition):
        if self._trick is None:
            self._trick = Trick(self._current_trump)
        self._trick.play_card(card, player)

        if self._trick.is_full:
            team_id = self._trick.get_owner() % 2
            self._scores[team_id] += self._trick.score
            self._past_tricks.append(self._trick)
            self._trick = None

    def choose_trump_suit(self, first: bool) -> Suit:
        suit_number = self._strategy.choose_trump_suit(self._hand, first)
        return Suit(suit_number)

    def choose_card(self) -> int:
        if self._trick is None:
            self._trick = Trick(self._current_trump)
        legal_moves = self._get_legal_moves()
        return self._strategy.choose_card(legal_moves, self._hand)

    def get_state_as_tokens(self) -> list[int]:
        return []

    def _compute_announcements(self):
        self._announcements = Announcement.find_announcements(self._hand)
        stoeck = (Card.from_rank_color(RANK_DAME, self._current_trump),
                  Card.from_rank_color(RANK_ROI, self._current_trump))
        self._has_stoeck = sum(c in stoeck for c in self._hand) == 2

    def _get_legal_moves(self):
        legal = []
        for card in self._hand:
            if self._trick.can_play(card, self._hand):
                legal.append(card)
        return legal
