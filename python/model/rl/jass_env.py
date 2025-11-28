from typing import Optional, Tuple, Dict, List
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

JASSFORMER_MODEL_PATH = "/home/jrb/src/external/jass/python/model/state_dict.pth"

class JassEnv(gym.Env):
    """
    A multi-agent (simulated) Jass environment wrapped in the Gymnasium API.
    The environment is designed to train Agent 0 against three static opponents.
    """
    metadata = {"render_modes": ["human"], "render_fps": 30}

    def __init__(self, render_mode: Optional[str] = None):
        super().__init__()

        self.scores = [0, 0]

        # 1. Initialize Player and Controller components
        self._teacher_model = JassFormerActorCritic(d_model=256)
        JassFormerActorCritic.load_policy_weights(self._teacher_model, JASSFORMER_MODEL_PATH)

        self._student_model = JassFormerActorCritic(d_model=256)
        JassFormerActorCritic.load_policy_weights(self._student_model, JASSFORMER_MODEL_PATH)

        self.players = None

        self._controller = Controller(self.players)

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

    def reset(self, seed: Optional[int] = None, options: Optional[Dict] = None) -> Tuple[np.ndarray, Dict]:
        super().reset(seed=seed)

        print(" <<<<<<<<<<<<<<<<<<<<<<<< New Game >>>>>>>>>>>>>>>>>>>>>>>>>>>>>")

        # randomly assign player strategies
        self.players = self.randomize_players()
        print(f"Roster: {",".join(map(lambda p:str(p._strategy), self.players))}")
        self._controller.set_players(self.players)

        # Reset game state and initial scores
        self._controller.reset()
        while self._controller.current_player != 0:
            self._controller.play_next_turn()

        observation = self._controller.get_current_observation(0)
        info = {}

        if self.render_mode == "human":
            self._render_frame()

        self.scores = [0, 0]

        return observation, info

    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict]:
        # action is the single integer from the action_space (0-40)
        terminated = False
        # 1. Check if the action is a Card Play (0-35) or Trump Choice (36-40)
        is_trump_action = action >= 36

        reward = 0

        # 2. Execute the move for the current player (Agent 0)
        if is_trump_action:
            chosen_suit = action - 36  # 0-3 are Suits, 4 is Pass/None
            self._controller.play_next_turn(chosen_suit)
        else:  # Card Action
            _, terminated = self._controller.play_next_turn(action)

        # play other players' turn
        while not terminated and self._controller.current_player != 0:
            _, terminated = self._controller.play_next_turn()

        if terminated:
            # Reward is based on team scores at the end of the game
            our_team_score, opp_team_score = self._controller.scores

            # Simple Win/Loss/Draw Reward (Standard RL setup)
            if our_team_score > opp_team_score:
                reward = 10.0
            elif our_team_score < opp_team_score:
                reward = -10.0
            else:
                reward = 0.0
            print(f"Reward after game: {reward}")
            observation = np.zeros(self.observation_space.shape, dtype=self.observation_space.dtype)
        else:
            observation = self._controller.get_current_observation(0)
            last_trick = self._controller.last_trick
            if not is_trump_action:
                if last_trick is None:
                    # hand finished
                    game_scores = [ self._controller.scores[i] - self.scores[i] for i in range(2)]
                    diff = game_scores[0] - game_scores[1]
                    reward = diff / 157
                    self.scores = [s for s in self._controller.scores]
                    print(f"Reward after round finished: {reward}")
                else:
                    if last_trick.owner % 2 == 0:
                        reward = self._controller.last_trick.score / 157 * 0.1
                    else:
                        reward = self._controller.last_trick.score / 157 * -0.1
                    print(f"Reward after trick: {reward}")

        # We are using a fully-defined game (Jass), so 'truncated' is generally False.
        truncated = False

        info = {"score": self._controller.scores}

        if self.render_mode == "human":
            self._render_frame()

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
        print("✅ Policy weights successfully updated into Actor-Critic model.")

    def randomize_players(self) -> list[Player]:
        weights = [0.4, 0.3, 0.2, 0.1]
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
