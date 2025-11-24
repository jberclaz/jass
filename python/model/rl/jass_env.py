from typing import Optional, Tuple, Dict, List

import gymnasium as gym
import numpy as np
from gymnasium import spaces

# Import your core game logic classes
from rl.controller import Controller
from model import JassFormerActorCritic
from rl.player import Player
from rl.rl_agent import RLAgent
from rl.strategy import Strategy, RandomStrategy

JASSFORMER_MODEL_PATH = "/home/jrb/src/external/jass/python/model/state_dict.pth"

class JassEnv(gym.Env):
    """
    A multi-agent (simulated) Jass environment wrapped in the Gymnasium API.
    The environment is designed to train Agent 0 against three static opponents.
    """
    metadata = {"render_modes": ["human"], "render_fps": 30}

    def __init__(self, render_mode: Optional[str] = None):
        super().__init__()

        # 1. Initialize Player and Controller components
        self.players: List[Player] = [Player(Strategy())]
        model = JassFormerActorCritic(d_model=256)
        JassFormerActorCritic.load_policy_weights(model, JASSFORMER_MODEL_PATH)
        for i in range(1, 4):
            self.players.append(Player(RLAgent(model)))
            #self.players.append(Player(RandomStrategy()))

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

        # Reset game state and initial scores
        self._controller.reset()
        while self._controller.current_player != 0:
            self._controller.play_next_turn()

        observation = self._controller.get_current_observation(0)
        info = {}

        if self.render_mode == "human":
            self._render_frame()

        return observation, info

    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict]:
        # action is the single integer from the action_space (0-40)
        terminated = False
        # 1. Check if the action is a Card Play (0-35) or Trump Choice (36-40)
        is_trump_action = action >= 36

        # 2. Execute the move for the current player (Agent 0)
        if is_trump_action:
            chosen_suit = action - 36  # 0-3 are Suits, 4 is Pass/None
            self._controller.play_next_turn(chosen_suit)

            # The Controller's play_next_turn method handles the rest of the trump logic
            # (partner choice, moving to next player, setting trump for all players)

            # We skip the explicit move execution here, as the Controller's logic is complex
            # Instead, we rely on the Controller's internal turn management to process the action
            # The Controller's logic needs to be modified to accept an action directly if running RLAgent in the loop.

            # --- CRITICAL INTEGRATION POINT ---
            # Since the Controller calls player.choose_trump_suit, we must rely on
            # the RLAgent's choose_trump_suit returning the action given here.
            # This requires a complex change to your existing Controller/RLAgent structure.
            #
            # For simplicity in this env wrapper, we will simulate the whole turn
            self._controller.play_next_turn()  # Assumes the current player is the RLAgent

        else:  # Card Action
            # The Controller's play_next_turn method needs to be executed
            # Since Controller.play_next_turn already contains the logic for RLAgent
            # when controller._current_player == 0, we rely on that.
            _, terminated = self._controller.play_next_turn(action)

        # play other players' turn
        while not terminated and self._controller.current_player != 0:
            _, terminated = self._controller.play_next_turn()

        if terminated:
            # Reward is based on team scores at the end of the game
            our_team_score, opp_team_score = self._controller.scores

            # Simple Win/Loss/Draw Reward (Standard RL setup)
            if our_team_score > opp_team_score:
                reward = 1.0
            elif our_team_score < opp_team_score:
                reward = -1.0
            else:
                reward = 0.0
            observation = None
        else:
            observation = self._controller.get_current_observation(0)
            reward = 0.0

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