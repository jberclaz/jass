import random

from rl.jass_rules import Card


class Strategy:
    def __init__(self):
        self._player = None

    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        pass

    def choose_trump_suit(self, hand: list[Card], first: bool):
        pass

    def set_player(self, player: 'Player'):
        self._player = player

class RandomStrategy(Strategy):
    def __init__(self):
        super().__init__()

    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        return random.choice(legal_moves).number

    def choose_trump_suit(self, hand: list[Card], first: bool):
        return random.randint(0, 3 if first else 4)
