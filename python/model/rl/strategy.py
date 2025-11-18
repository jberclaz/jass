import random

from rl.jass_rules import Card


class Strategy:
    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        pass

    def choose_trump_suit(self, hand: list[Card], first: bool):
        pass

class RandomStrategy(Strategy):
    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        return random.randint(0, 36)

    def choose_trump_suit(self, hand: list[Card], first: bool):
        return random.randint(0, 5 if first else 4)
