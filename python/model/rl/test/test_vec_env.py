import contextlib
import io
import os
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
import torch

import rl.jass_env
from model import JassFormer, JassFormerActorCritic
from rl.jass_env import JassEnv
from rl.jass_rules import Card
from rl.jass_vec_env import JassVecEnv
from rl.tokens import Tokens


class TestJassVecEnv(unittest.TestCase):
    def setUp(self):
        # opponents and agent use a randomly initialized model
        torch.manual_seed(0)
        np.random.seed(0)
        self._tmp_dir = tempfile.TemporaryDirectory()
        checkpoint = os.path.join(self._tmp_dir.name, "random.pth")
        torch.save(JassFormer(d_model=256).state_dict(), checkpoint)
        self._path_patch = patch.object(rl.jass_env, "JASSFORMER_MODEL_PATH", checkpoint)
        self._path_patch.start()
        self.agent = JassFormerActorCritic(d_model=256).eval()

    def tearDown(self):
        self._path_patch.stop()
        self._tmp_dir.cleanup()

    def test_games_with_batched_opponents(self):
        num_envs = 4
        opponent_moves = 0
        forward_calls = 0
        original_play_opponent_turn = JassEnv.play_opponent_turn
        original_get_action_and_value = JassFormerActorCritic.get_action_and_value

        def checked_play_opponent_turn(env, forced_action=None):
            nonlocal opponent_moves
            opponent_moves += 1
            if forced_action is not None and not env._controller.is_trump_selection:
                player = env.players[env.waiting_player]
                legal = player._get_legal_moves() if player._trick is not None else player._hand
                self.assertIn(Card(forced_action), legal, "batched opponent played an illegal card")
            original_play_opponent_turn(env, forced_action)

        def counting_get_action_and_value(model, x, action=None):
            nonlocal forward_calls
            if model is not self.agent:
                forward_calls += 1
            return original_get_action_and_value(model, x, action)

        with patch.object(JassEnv, "play_opponent_turn", checked_play_opponent_turn), \
                patch.object(JassFormerActorCritic, "get_action_and_value", counting_get_action_and_value), \
                contextlib.redirect_stdout(io.StringIO()):
            envs = JassVecEnv(num_envs)
            obs, _ = envs.reset(seed=1)
            returns = np.zeros(num_envs)
            finished_episodes = 0
            for _ in range(300):
                self.assertTrue(np.all(obs[:, 0] == Tokens.CLS), "every observation must be a real game state")
                with torch.no_grad():
                    actions, _, _, _ = self.agent.get_action_and_value(torch.from_numpy(obs))
                obs, rewards, terminations, truncations, infos = envs.step(actions.numpy())
                returns += rewards
                if "final_info" in infos:
                    final_info = infos["final_info"]
                    for i in np.flatnonzero(final_info["_episode"]):
                        self.assertTrue(terminations[i])
                        self.assertAlmostEqual(final_info["episode"]["r"][i], returns[i], places=5)
                        returns[i] = 0
                        finished_episodes += 1
            envs.close()

        self.assertGreater(finished_episodes, 0)
        self.assertLess(forward_calls, opponent_moves, "opponent moves should be batched")

    def test_hand_episodes(self):
        num_envs = 4
        final_scores = {}
        original_finish_step = JassEnv.finish_step

        def recording_finish_step(env):
            result = original_finish_step(env)
            if result[2]:  # terminated: the scores of the hand, before the autoreset
                final_scores[id(env)] = list(env._controller.scores)
            return result

        with patch.object(JassEnv, "finish_step", recording_finish_step):
            envs = JassVecEnv(num_envs, hand_episodes=True)
            obs, _ = envs.reset(seed=1)
            returns = np.zeros(num_envs)
            finished_hands = 0
            for _ in range(60):
                with torch.no_grad():
                    actions, _, _, _ = self.agent.get_action_and_value(torch.from_numpy(obs))
                obs, rewards, terminations, truncations, infos = envs.step(actions.numpy())
                returns += rewards
                for i in np.flatnonzero(terminations):
                    episode = infos["final_info"]["episode"]
                    ours, theirs = final_scores[id(envs.envs[i])]
                    # the return is the point difference of the hand, announcements, last trick and match included
                    self.assertAlmostEqual(returns[i] * 157, ours - theirs, places=3)
                    self.assertGreaterEqual(ours + theirs, 157)
                    # 9 cards, plus a trump choice when the agent chooses
                    self.assertIn(episode["l"][i], (9, 10))
                    self.assertEqual(episode["won"][i], 0.5 + np.sign(ours - theirs) / 2)
                    returns[i] = 0
                    finished_hands += 1
            envs.close()

        self.assertGreaterEqual(finished_hands, 4 * 5)


if __name__ == "__main__":
    unittest.main()
