from rl.jass_rules import Card, Suit, PlayerPosition
from rl.strategy import Strategy


class Player:
    def __init__(self, strategy: Strategy):
        self._strategy = strategy
        self._current_trump = Suit.NONE
        self._hand = []
        self._trick = None
        self._past_tricks = []
        self._score = 0
        self._opponent_score = 0

    def set_hand(self, hand: list[Card]):
        self._hand = hand
        self._compute_announcements()

    def set_trump_suit(self, suit: Suit):
        self._current_trump = suit

    def set_announcements(self, announcements):
        pass

    def get_announcements(self):
        pass

    def played(self, card: Card, player: PlayerPosition):
        pass

    def choose_trump_suit(self, first: bool) -> Suit:
        return self._strategy.choose_trump_suit(self._hand, first)

    def choose_card(self) -> Card:
        return self._strategy.choose_card()

    def _compute_announcements(self):
        pass
