import contextlib
import io
import os
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
import torch

import rl.jass_env
from dataset import TOKEN_LENGTH, VOCABULARY_SIZE
from model import (JassFormer, JassFormerActorCritic, JassCritic, CRITIC_HAND_SLOTS, CRITIC_TOKEN_LENGTH, MAX_SEAT_MODELS,
                   seat_model_token)
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

    def test_opponent_pool(self):
        num_envs = 8
        other = os.path.join(self._tmp_dir.name, "other.pth")
        torch.save(JassFormer(d_model=256).state_dict(), other)
        envs = JassVecEnv(num_envs, hand_episodes=True, opponent_model_paths=[rl.jass_env.JASSFORMER_MODEL_PATH, other],
                          pool_size=2, fixed_opponent_fraction=0.5)
        self.assertEqual(envs.fixed_opponent_groups, ["random", "other"])
        snapshots = [JassFormerActorCritic(d_model=256).state_dict() for _ in range(3)]
        for snapshot in snapshots:
            envs.add_opponent_snapshot(snapshot)
        # the oldest snapshot is dropped
        self.assertEqual(envs.pool_length, 2)
        self.assertTrue(torch.equal(envs._pool[0].embedding.weight.cpu(), snapshots[1]["embedding.weight"]))

        pool_models = {id(model) for model in envs._pool}
        used_pool_models = set()
        obs, _ = envs.reset(seed=1)
        opponents = []
        for step in range(300):
            if step % 30 == 0:
                envs.next_pool_opponent()  # as PPO does at each iteration
            opponent_models = set()
            for env in envs.envs:
                # one model for the 3 other players
                models = {id(player._strategy.model) for player in env.players[1:]}
                self.assertEqual(len(models), 1)
                opponent_models |= models
            # a single snapshot at a time, plus the previous one in the games that started before the draw
            self.assertLessEqual(len(opponent_models & pool_models), 2)
            used_pool_models |= opponent_models & pool_models
            with torch.no_grad():
                actions, _, _, _ = self.agent.get_action_and_value(torch.from_numpy(obs))
            obs, rewards, terminations, truncations, infos = envs.step(actions.numpy())
            if "final_info" in infos:
                final_info = infos["final_info"]
                opponents += final_info["episode"]["opponent"][final_info["_episode"]].tolist()
        envs.close()
        self.assertGreater(len(opponents), 100)
        self.assertEqual(set(opponents), {"random", "other", "pool"})
        self.assertAlmostEqual(opponents.count("pool") / len(opponents), 0.5, delta=0.15)
        self.assertEqual(used_pool_models, pool_models)


    def test_mixed_seats(self):
        num_envs = 8
        other = os.path.join(self._tmp_dir.name, "other.pth")
        torch.save(JassFormer(d_model=256).state_dict(), other)
        envs = JassVecEnv(num_envs, hand_episodes=True, opponent_model_paths=[rl.jass_env.JASSFORMER_MODEL_PATH, other],
                          pool_size=1, fixed_opponent_fraction=0.5, mixed_seat_fraction=0.5)
        envs.add_opponent_snapshot(JassFormerActorCritic(d_model=256).state_dict())
        envs.next_pool_opponent()
        models_by_id = {0: envs._fixed_opponents[0][0], 1: envs._fixed_opponents[1][0], 2: envs._pool[0]}
        obs, _ = envs.reset(seed=1)
        groups, mixed_with_different_models = [], 0
        for _ in range(300):
            for env in envs.envs:
                models = [player._strategy.model for player in env.players[1:]]
                # the model ids name the models that play the seats
                self.assertEqual([models_by_id[i] for i in env.seat_model_ids], models)
                if env.opponent_group != "mixed":
                    self.assertEqual(len({id(m) for m in models}), 1)
                elif len({id(m) for m in models}) > 1:
                    mixed_with_different_models += 1
            with torch.no_grad():
                actions, _, _, _ = self.agent.get_action_and_value(torch.from_numpy(obs))
            obs, rewards, terminations, truncations, infos = envs.step(actions.numpy())
            if "final_info" in infos:
                final_info = infos["final_info"]
                groups += final_info["episode"]["opponent"][final_info["_episode"]].tolist()
        envs.close()
        self.assertEqual(set(groups), {"random", "other", "pool", "mixed"})
        self.assertAlmostEqual(groups.count("mixed") / len(groups), 0.5, delta=0.15)
        self.assertGreater(mixed_with_different_models, 0)

    def test_critic_observations(self):
        num_envs = 4
        envs = JassVecEnv(num_envs, hand_episodes=True, critic_observations=True)
        obs, infos = envs.reset(seed=1)
        finished = 0
        for _ in range(100):
            critic_obs = infos["critic_obs"]
            self.assertEqual(critic_obs.shape, (num_envs,) + envs.single_critic_observation_shape)
            for i, env in enumerate(envs.envs):
                # the agent's tokens, then the hands of players 1, 2 and 3, then their models
                np.testing.assert_array_equal(critic_obs[i, :TOKEN_LENGTH], obs[i])
                for seat in range(3):
                    start = TOKEN_LENGTH + seat * CRITIC_HAND_SLOTS
                    hand = sorted(card.number for card in env.players[seat + 1]._hand)
                    slots = critic_obs[i, start:start + CRITIC_HAND_SLOTS]
                    self.assertEqual(slots[:len(hand)].tolist(), [Tokens.card_token(c) for c in hand])
                    self.assertTrue(np.all(slots[len(hand):] == Tokens.PAD))
                self.assertEqual(critic_obs[i, -3:].tolist(), [seat_model_token(0)] * 3)
            with torch.no_grad():
                actions, _, _, _ = self.agent.get_action_and_value(torch.from_numpy(obs))
            obs, rewards, terminations, truncations, infos = envs.step(actions.numpy())
            # after an autoreset, the observations are the first ones of the next hand: so are the critic's
            finished += int(terminations.sum())
        envs.close()
        self.assertGreater(finished, 0)

    def test_critic_starts_from_the_agent(self):
        agent = JassFormerActorCritic(d_model=256)
        critic = JassCritic(d_model=256)
        critic.init_from_actor_critic(agent)
        self.assertTrue(torch.equal(critic.embedding.weight[:VOCABULARY_SIZE], agent.embedding.weight))
        self.assertTrue(torch.equal(critic.pos_embedding[:, :TOKEN_LENGTH], agent.pos_embedding))
        self.assertTrue(torch.equal(critic.value_head.weight, agent.value_head.weight))
        for name, param in agent.transformer.state_dict().items():
            self.assertTrue(torch.equal(critic.transformer.state_dict()[name], param), name)
        tokens = torch.zeros((2, CRITIC_TOKEN_LENGTH), dtype=torch.int64)
        tokens[:, 0] = Tokens.CLS
        tokens[:, -3:] = seat_model_token(MAX_SEAT_MODELS - 1)
        self.assertEqual(critic(tokens).shape, (2,))


if __name__ == "__main__":
    unittest.main()
