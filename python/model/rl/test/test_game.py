import unittest

from rl.controller import Controller
from rl.player import Player
from rl.strategy import RandomStrategy
from rl.rl_agent import RLAgent
from model import JassFormerActorCritic

class TestGame(unittest.TestCase):
    def test_game(self):
        model = JassFormerActorCritic(d_model=256)
        JassFormerActorCritic.load_policy_weights(model, "state_dict.pth")
        players = [Player(RLAgent(model)) for _ in range(4)]
        controller = Controller(players)

        controller.reset()

        while True:
            tokens, action, game_over = controller.get_next_tokens(0)
            if game_over:
                break
