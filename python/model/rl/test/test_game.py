import unittest

from rl.controller import Controller
from rl.player import Player
from rl.strategy import RandomStrategy


class TestGame(unittest.TestCase):
    def test_game(self):
        players = [Player(RandomStrategy()) for i in range(4)]
        controller = Controller(players)

        controller.reset()

        while True:
            tokens, action, game_over = controller.get_next_tokens(0)
            if game_over:
                break
