from typing import Optional

import gymnasium as gym
import numpy as np
import torch
from gymnasium.vector import AutoresetMode
from gymnasium.vector.utils import batch_space

from rl.jass_env import JassEnv
from rl.rl_agent import RLAgent


class JassVecEnv(gym.vector.VectorEnv):
    """
    Runs several JassEnv games in lockstep. The opponents played by a model are batched: whenever several games are
    waiting on opponents using the same model, their next moves are computed with a single forward pass on `device`.

    Autoresets like SyncVectorEnv with AutoresetMode.SAME_STEP, and reports episode statistics the same way as
    RecordEpisodeStatistics does in that mode (infos["final_info"]["episode"], masked by infos["final_info"]["_episode"]),
    plus "won": 1 if the agent's team won the game, 0.5 on a tie, 0 if it lost.
    """

    metadata = {"autoreset_mode": AutoresetMode.SAME_STEP}

    def __init__(self, num_envs: int, device: torch.device | str = "cpu", supervised_model_path: Optional[str] = None):
        self.device = torch.device(device)
        # a single teacher and a single student model, shared by all the games
        self._teacher_model = JassEnv._load_opponent_model(supervised_model_path).to(self.device)
        self._student_model = JassEnv._load_opponent_model(supervised_model_path).to(self.device)
        self.envs = [JassEnv(teacher_model=self._teacher_model, student_model=self._student_model)
                     for _ in range(num_envs)]

        self.num_envs = num_envs
        self.single_observation_space = self.envs[0].observation_space
        self.single_action_space = self.envs[0].action_space
        self.observation_space = batch_space(self.single_observation_space, num_envs)
        self.action_space = batch_space(self.single_action_space, num_envs)

        self._episode_returns = np.zeros(num_envs, dtype=np.float64)
        self._episode_lengths = np.zeros(num_envs, dtype=np.int64)

    def reset(self, *, seed: Optional[int | list[int]] = None, options: Optional[dict] = None):
        seeds = seed if isinstance(seed, list) else [None if seed is None else seed + i for i in range(self.num_envs)]
        for env, env_seed in zip(self.envs, seeds):
            env.start_game(env_seed)
        self._play_opponents(range(self.num_envs))
        self._episode_returns[:] = 0
        self._episode_lengths[:] = 0
        return np.stack([env.agent_observation() for env in self.envs]), {}

    def step(self, actions):
        for env, action in zip(self.envs, actions):
            env.apply_agent_action(int(action))
        self._play_opponents(range(self.num_envs))

        observations = np.zeros((self.num_envs,) + self.single_observation_space.shape,
                                dtype=self.single_observation_space.dtype)
        rewards = np.zeros(self.num_envs, dtype=np.float64)
        terminations = np.zeros(self.num_envs, dtype=np.bool_)
        truncations = np.zeros(self.num_envs, dtype=np.bool_)
        won = np.zeros(self.num_envs, dtype=np.float64)
        for i, env in enumerate(self.envs):
            observations[i], rewards[i], terminations[i], truncations[i], info = env.finish_step()
            our_score, their_score = info["score"]
            won[i] = 0.5 + np.sign(our_score - their_score) / 2
        self._episode_returns += rewards
        self._episode_lengths += 1

        infos = {}
        finished = np.flatnonzero(terminations | truncations)
        if len(finished) > 0:
            done_mask = terminations | truncations
            infos["final_info"] = {
                "episode": {
                    "r": np.where(done_mask, self._episode_returns, 0.0),
                    "l": np.where(done_mask, self._episode_lengths, 0),
                    "won": np.where(done_mask, won, 0.0),
                },
                "_episode": done_mask,
            }
            infos["_final_info"] = done_mask
            # autoreset: the returned observation is the first one of the next game
            for i in finished:
                self.envs[i].start_game()
            self._play_opponents(finished)
            for i in finished:
                observations[i] = self.envs[i].agent_observation()
            self._episode_returns[finished] = 0
            self._episode_lengths[finished] = 0

        return observations, rewards, terminations, truncations, infos

    def update_opponent_model(self, state_dict):
        self._student_model.load_state_dict(state_dict)

    def close_extras(self, **kwargs):
        for env in self.envs:
            env.close()

    def _play_opponents(self, env_indices):
        """Plays the opponents' turns in the given games, until it is the agent's turn (or the game is over)."""
        pending = [i for i in env_indices if self.envs[i].waiting_player is not None]
        while pending:
            # group the games by the model of the opponent who has to play
            by_model: dict[int, tuple[torch.nn.Module, list[int]]] = {}
            for i in pending:
                env = self.envs[i]
                strategy = env.players[env.waiting_player]._strategy
                if isinstance(strategy, RLAgent):
                    by_model.setdefault(id(strategy.model), (strategy.model, []))[1].append(i)
                else:
                    env.play_opponent_turn()  # random / heuristic strategies play on their own

            for model, indices in by_model.values():
                tokens = torch.from_numpy(np.stack([self.envs[i].opponent_observation() for i in indices]))
                with torch.no_grad():
                    actions, _, _, _ = model.get_action_and_value(tokens.to(self.device))
                for i, action in zip(indices, actions.tolist()):
                    # actions 0-35 are cards, 36-40 trump choices (suit 0-3 or pass)
                    self.envs[i].play_opponent_turn(action if action < 36 else action - 36)

            pending = [i for i in pending if self.envs[i].waiting_player is not None]
