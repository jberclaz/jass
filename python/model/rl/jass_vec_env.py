import os
import random
from typing import Optional

import gymnasium as gym
import numpy as np
import torch
from gymnasium.vector import AutoresetMode
from gymnasium.vector.utils import batch_space

from model import JassFormerActorCritic
from rl.jass_env import JassEnv
from rl.rl_agent import RLAgent


def opponent_groups(paths: list[Optional[str]]) -> list[str]:
    """Names of the fixed opponents, for the metrics: their file names without extension, numbered when equal."""
    names = []
    for path in paths:
        stem = os.path.splitext(os.path.basename(path))[0] if path else "teacher"
        name, i = stem, 1
        while name in names:
            i += 1
            name = f"{stem}_{i}"
        names.append(name)
    return names


class JassVecEnv(gym.vector.VectorEnv):
    """
    Runs several JassEnv games in lockstep. The opponents played by a model are batched: whenever several games are
    waiting on opponents using the same model, their next moves are computed with a single forward pass on `device`.

    For each game, a single model plays the 3 other players (partner included): one of the fixed opponents (the
    supervised model by default), or, with probability 1 - fixed_opponent_fraction, one of the last pool_size
    snapshots of the agent (add_opponent_snapshot). infos["final_info"]["episode"]["opponent"] names it: the fixed
    opponent's group (opponent_groups), or "pool".

    Autoresets like SyncVectorEnv with AutoresetMode.SAME_STEP, and reports episode statistics the same way as
    RecordEpisodeStatistics does in that mode (infos["final_info"]["episode"], masked by infos["final_info"]["_episode"]),
    plus "won": 1 if the agent's team won the episode (game or hand), 0.5 on a tie, 0 if it lost.
    """

    metadata = {"autoreset_mode": AutoresetMode.SAME_STEP}

    def __init__(self, num_envs: int, device: torch.device | str = "cpu", supervised_model_path: Optional[str] = None,
                 hand_episodes: bool = False, opponent_model_paths: Optional[list[str]] = None, pool_size: int = 8,
                 fixed_opponent_fraction: float = 0.5):
        self.device = torch.device(device)
        # the models are shared by all the games
        paths = opponent_model_paths or [supervised_model_path]
        self._fixed_opponents = [(JassEnv._load_opponent_model(path).to(self.device), group)
                                 for path, group in zip(paths, opponent_groups(paths))]
        self._pool: list[JassFormerActorCritic] = []
        self._pool_size = pool_size
        self._fixed_opponent_fraction = fixed_opponent_fraction
        first_model = self._fixed_opponents[0][0]
        self.envs = [JassEnv(teacher_model=first_model, student_model=first_model, hand_episodes=hand_episodes)
                     for _ in range(num_envs)]
        for env in self.envs:
            env.opponent_chooser = self._choose_opponent

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
        opponents = np.empty(self.num_envs, dtype=object)
        for i, env in enumerate(self.envs):
            observations[i], rewards[i], terminations[i], truncations[i], info = env.finish_step()
            our_score, their_score = info["score"]
            won[i] = 0.5 + np.sign(our_score - their_score) / 2
            opponents[i] = env.opponent_group  # before the autoreset draws the next opponent
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
                    "opponent": np.where(done_mask, opponents, None),
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

    def _choose_opponent(self) -> tuple[JassFormerActorCritic, str]:
        if self._pool and random.random() >= self._fixed_opponent_fraction:
            return random.choice(self._pool), "pool"
        return random.choice(self._fixed_opponents)

    def add_opponent_snapshot(self, state_dict):
        """Adds a copy of the agent to the pool of opponents, replacing the oldest one when the pool is full."""
        if self._pool_size <= 0:
            return
        # a new model rather than overwriting the oldest one: games in progress keep their opponent until the end
        model = JassFormerActorCritic(d_model=state_dict["embedding.weight"].shape[1])
        model.load_state_dict(state_dict)
        self._pool.append(model.to(self.device).eval())
        del self._pool[:-self._pool_size]

    @property
    def fixed_opponent_groups(self) -> list[str]:
        return [group for _, group in self._fixed_opponents]

    @property
    def pool_length(self) -> int:
        return len(self._pool)

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
