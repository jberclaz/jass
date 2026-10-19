import unittest
from unittest.mock import patch

from rl.controller import Controller
from rl.player import Player
from rl.strategy import RandomStrategy
from rl.trick import Trick


class TestController(unittest.TestCase):
    def _play_hand(self, start_score: int) -> tuple[bool, list[int]]:
        """
        Plays a hand from start_score - start_score where the tricks are worth 0 points and nobody announces: only the
        last trick and match bonuses change the scores. Returns whether the game is over at the end of the hand.
        """
        players = [Player(RandomStrategy()) for _ in range(4)]
        controller = Controller(players)
        controller.reset()
        controller._scores = [start_score, start_score]
        for player in players:
            player._scores = [start_score, start_score]
        with patch.object(Trick, "score", property(lambda trick: 0)), \
                patch.object(Player, "get_announcements", lambda player: []):
            cards = 0
            while cards < 36:
                trump_selection = controller.is_trump_selection
                _, game_over = controller.play_next_turn()
                if not trump_selection:
                    cards += 1
                    self.assertTrue(cards == 36 or not game_over, "the game cannot end before the bonuses")
        return game_over, list(controller.scores)

    def test_bonuses_end_the_game(self):
        # the team that takes the last trick gets at least 5 points: 1500
        game_over, scores = self._play_hand(1495)
        self.assertTrue(game_over)
        self.assertGreaterEqual(max(scores), 1500)

    def test_game_goes_on(self):
        game_over, scores = self._play_hand(1000)
        self.assertFalse(game_over)
        self.assertLess(max(scores), 1500)


if __name__ == "__main__":
    unittest.main()
