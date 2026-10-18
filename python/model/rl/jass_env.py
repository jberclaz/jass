import logging
from typing import Callable, Optional, Tuple, Dict, List
import random
import gymnasium as gym
import numpy as np
from gymnasium import spaces

# Import your core game logic classes
from rl.controller import Controller
from model import JassFormerActorCritic
from rl.player import Player
from rl.rl_agent import RLAgent
from rl.strategy import Strategy, RandomStrategy, HeuristicStrategy

logger = logging.getLogger(__name__)

JASSFORMER_MODEL_PATH = "/home/jrb/src/external/jass/python/model/state_dict.pth"

class JassEnv(gym.Env):
    """
    A multi-agent (simulated) Jass environment wrapped in the Gymnasium API.
    The environment is designed to train Agent 0 against three static opponents.

    With hand_episodes, an episode is a single hand from a score of 0 - 0, and each step is rewarded with the change of
    the point difference (agent's team - opponents, announcements, last trick and match included) / 157 since the
    agent's previous step: the return of an episode is the point difference of the hand / 157. Otherwise an episode is
    a game to 1500 points, rewarded +-10 at the end, with the point difference of each hand and a bonus per trick.

    step() and reset() are split into parts (apply_agent_action, play_opponent_turn, finish_step, ...)
    so that JassVecEnv can drive several games and batch the opponents' model inference.
    """
    metadata = {"render_modes": ["human"], "render_fps": 30}

    def __init__(self, render_mode: Optional[str] = None,
                 teacher_model: Optional[JassFormerActorCritic] = None,
                 student_model: Optional[JassFormerActorCritic] = None,
                 supervised_model_path: str = JASSFORMER_MODEL_PATH,
                 hand_episodes: bool = False):
        super().__init__()
        self._hand_episodes = hand_episodes
        self._last_score_difference = 0

        # 1. Initialize Player and Controller components (opponent models can be shared between environments)
        if teacher_model is None:
            teacher_model = self._load_opponent_model(supervised_model_path)
        if student_model is None:
            student_model = self._load_opponent_model(supervised_model_path)
        self._teacher_model = teacher_model
        self._student_model = student_model
        # if set, called at the start of each game: (model, group) of the 3 other players, partner included; else
        # each seat is drawn on its own (randomize_players)
        self.opponent_chooser: Optional[Callable[[], Tuple[JassFormerActorCritic, str]]] = None
        self.opponent_group: Optional[str] = None

        self.players = None

        self._controller = Controller(self.players, single_hand=hand_episodes)
        self._terminated = False
        self._last_action_was_trump = False

        # 2. Define Observation Space (State Tokens)
        # Assuming TOKEN_LENGTH is defined in your dataset/model setup
        from dataset import TOKEN_LENGTH
        self.observation_space = spaces.Box(
            low=0, high=128, shape=(TOKEN_LENGTH,), dtype=np.int64
        )

        # 3. Define Action Space (Single output for Card or Trump)
        # Card Action (0-35) or Trump Action (36-40)
        self.action_space = spaces.Discrete(36 + 5)  # 41 total actions

        self.render_mode = render_mode

    @staticmethod
    def _load_opponent_model(supervised_model_path: str = None) -> JassFormerActorCritic:
        model = JassFormerActorCritic(d_model=256)
        JassFormerActorCritic.load_policy_weights(model, supervised_model_path or JASSFORMER_MODEL_PATH)
        model.eval()
        return model

    def reset(self, seed: Optional[int] = None, options: Optional[Dict] = None) -> Tuple[np.ndarray, Dict]:
        self.start_game(seed)
        while self.waiting_player is not None:
            self.play_opponent_turn()

        observation = self.agent_observation()
        info = {}

        if self.render_mode == "human":
            self._render_frame()

        return observation, info

    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict]:
        # action is the single integer from the action_space (0-40)
        self.apply_agent_action(action)

        # play other players' turn
        while self.waiting_player is not None:
            self.play_opponent_turn()

        observation, reward, terminated, truncated, info = self.finish_step()

        if self.render_mode == "human":
            self._render_frame()

        return observation, reward, terminated, truncated, info

    def start_game(self, seed: Optional[int] = None):
        """Starts a new game with a new roster, up to the first opponent's turn (if any)."""
        super().reset(seed=seed)

        logger.debug(" <<<<<<<<<<<<<<<<<<<<<<<< New Game >>>>>>>>>>>>>>>>>>>>>>>>>>>>>")

        # randomly assign player strategies
        self.players = self.randomize_players()
        logger.debug(f"Roster: {",".join(map(lambda p:str(p._strategy), self.players))}")
        self._controller.set_players(self.players)

        # Reset game state and initial scores
        self._controller.reset()
        self._terminated = False
        self._last_score_difference = 0

    def apply_agent_action(self, action: int):
        """Plays the agent's (player 0) action: a card (0-35) or a trump choice (36-40)."""
        # 1. Check if the action is a Card Play (0-35) or Trump Choice (36-40)
        is_trump_action = action >= 36

        if is_trump_action != self._controller.is_trump_selection:
            raise ValueError(
                f"Action {action} inconsistent with phase "
                f"(trump_selection={self._controller.is_trump_selection})"
            )

        # 2. Execute the move for the current player (Agent 0)
        self._last_action_was_trump = is_trump_action
        if is_trump_action:
            chosen_suit = action - 36  # 0-3 are Suits, 4 is Pass/None
            self._controller.play_next_turn(chosen_suit)
        else:  # Card Action
            _, self._terminated = self._controller.play_next_turn(action)

    @property
    def waiting_player(self) -> Optional[int]:
        """The opponent (1-3) who has to play next, or None if it is the agent's turn or the game is over."""
        if self._terminated or self._controller.current_player == 0:
            return None
        return self._controller.current_player

    def opponent_observation(self) -> np.ndarray:
        return self._controller.get_current_observation(self.waiting_player)

    def play_opponent_turn(self, forced_action: Optional[int] = None):
        """
        Plays the waiting opponent's turn, with its own strategy or with the given action
        (a card 0-35, or a suit 0-4 during trump selection).
        """
        _, game_over = self._controller.play_next_turn(forced_action)
        self._terminated = self._terminated or game_over

    def agent_observation(self) -> np.ndarray:
        return self._controller.get_current_observation(0)

    def finish_step(self) -> Tuple[np.ndarray, float, bool, bool, Dict]:
        """Computes the result of the agent's last action, once it is the agent's turn again (or the game is over)."""
        reward = 0
        terminated = self._terminated

        if self._hand_episodes:
            our_team_score, opp_team_score = self._controller.scores
            difference = our_team_score - opp_team_score
            reward = (difference - self._last_score_difference) / 157
            self._last_score_difference = difference
            if terminated:
                observation = np.zeros(self.observation_space.shape, dtype=self.observation_space.dtype)
            else:
                observation = self.agent_observation()
        elif terminated:
            # Reward is based on team scores at the end of the game
            our_team_score, opp_team_score = self._controller.scores

            # Simple Win/Loss/Draw Reward (Standard RL setup)
            if our_team_score > opp_team_score:
                reward = 10.0
            elif our_team_score < opp_team_score:
                reward = -10.0
            else:
                reward = 0.0
            logger.debug(f"Reward after game: {reward}")
            observation = np.zeros(self.observation_space.shape, dtype=self.observation_space.dtype)
        else:
            observation = self.agent_observation()
            last_trick = self._controller.last_trick
            if not self._last_action_was_trump:
                if last_trick is None:
                    # hand finished
                    game_scores = self._controller.last_game_scores
                    diff = game_scores[0] - game_scores[1]
                    reward = diff / 157
                    logger.debug(f"Reward after round finished: {reward}")
                else:
                    if last_trick.owner % 2 == 0:
                        reward = self._controller.last_trick.score / 157 * 0.1
                    else:
                        reward = self._controller.last_trick.score / 157 * -0.1
                    logger.debug(f"Reward after trick: {reward}")

        # We are using a fully-defined game (Jass), so 'truncated' is generally False.
        truncated = False

        info = {"score": self._controller.scores}

        return observation, reward, terminated, truncated, info

    # The remaining methods (render, close) are standard for Gymnasium, but omitted for brevity.
    def render(self):
        if self.render_mode == "rgb_array":
            return self._render_frame()

    def _render_frame(self):
        # Placeholder for game visualization (not needed for training)
        pass

    def close(self):
        pass

    def update_opponent_model(self, state_dict):
        self._student_model.load_state_dict(state_dict)
        logger.debug("✅ Policy weights successfully updated into Actor-Critic model.")

    def randomize_players(self) -> list[Player]:
        if self.opponent_chooser is not None:
            model, self.opponent_group = self.opponent_chooser()
            return [Player(Strategy())] + [Player(RLAgent(model, name=self.opponent_group)) for _ in range(3)]
        weights = [0.95, 0.05, 0, 0]
        model_pool = [
            (self._teacher_model, "teacher"),
            (self._student_model, "student"),
            (None, "Random"), # None signals random strategy
            (None, "Heuristic"),
        ]
        players: List[Player] = [Player(Strategy())]
        for i in range(1, 4):
            # Sample a strategy
            chosen_model, name = random.choices(model_pool, weights=weights, k=1)[0]

            if chosen_model is None:
                players.append(Player(RandomStrategy() if name == "Random" else HeuristicStrategy()))
            else:
                players.append(Player(RLAgent(chosen_model, name=name)))
        return players
