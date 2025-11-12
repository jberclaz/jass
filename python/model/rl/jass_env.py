import gymnasium as gym
from gymnasium import spaces
import numpy as np
import torch

from model import JassFormerActorCritic
from dataset import TOKEN_LENGTH, VOCABULARY_SIZE
from legal_mask import get_legal_mask_with_rules_batch
from rl.utils import DIAMOND_SEVEN


# Define constants for card representations, players, etc.
# (e.g., card IDs 10-45, player IDs, etc.)
# ...

class JassEnv(gym.Env):
    """
    A custom Gymnasium environment for the Jass card game.
    """
    # This metadata is good practice
    metadata = {"render_modes": [], "render_fps": 1}

    def __init__(self, opponent_model: JassFormerActorCritic, device):
        """
        Initializes the Jass game environment.

        Args:
            opponent_model: The pre-trained JassFormer model to use for
                            simulating opponent moves.
            device: The torch device ('cuda' or 'cpu') to run the model on.
        """
        super().__init__()

        self.opponent_model = opponent_model
        self.device = device

        # --- 1. Define Action Space ---
        # The agent can choose 1 of 36 possible cards
        self.action_space = spaces.Discrete(36)

        # --- 2. Define Observation Space ---
        # The observation is our TOKEN_LENGTH (95) array
        # Values are integers from 0 to VOCABULARY_SIZE-1 (127)
        self.observation_space = spaces.Box(
            low=0,
            high=VOCABULARY_SIZE - 1,
            shape=(TOKEN_LENGTH,),
            dtype=np.int64
        )

        # --- 3. Internal Game State ---
        # You need to track everything about the current game.
        # This is just an example; your state will be more complex.
        self.deck = []
        self.trump_suit = None
        self.player_hands = [[] for _ in range(4)] # e.g., 4 players
        self.current_trick = []
        self.previous_tricks = []
        self.current_player_idx = 0
        self.game_scores = []
        self.match_scores = []
        self.game_state_tokens = np.zeros(TOKEN_LENGTH, dtype=np.int64)
        self.trump_chosen_on_first_turn = None

    def _get_obs(self):
        """
        Generates the observation (token array) from the internal game state.
        This is a crucial helper function.
        """
        # ...
        # Logic to build the 95-token array based on
        # self.player_hands, self.current_trick, trump suit, etc.
        # ...
        return self.game_state_tokens

    def _get_info(self):
        """
        Generates auxiliary info. The most important info is the legal mask.
        """
        # ...
        # Logic to calculate the legal mask for the current player
        # You can re-use your get_legal_mask_with_rules_batch logic here
        # ...
        # We need to compute this for the *current* state
        current_tokens_tensor = torch.tensor(
            self.game_state_tokens,
            dtype=torch.long,
            device=self.device
        ).unsqueeze(0) # Add batch dim

        with torch.no_grad():
            legal_mask = get_legal_mask_with_rules_batch(current_tokens_tensor).squeeze(0) # Remove batch dim

        return {"legal_mask": legal_mask.cpu().numpy()}

    def reset(self, seed=None, options=None):
        """
        Resets the environment to the start of a new game.
        """
        # Call super().reset() for compatibility
        super().reset(seed=seed)

        # --- Your Jass Game Setup Logic ---
        # 1. Create and shuffle the deck
        original_deck = np.arange(36)
        shuffled_deck = np.random.permutation(original_deck)

        # 2. Deal hands to all 4 players
        self.player_hands = shuffled_deck.reshape((4, 9))

        # 3. Determine trump suit, starting player, etc.
        self.current_player_idx = np.where(shuffled_deck == DIAMOND_SEVEN)[0] / 9

        # 4. Initialize all other game state variables
        self.current_trick = []
        self.previous_tricks = []
        self.trump_suit = None
        self.trump_chosen_on_first_turn = None

        # 5. Generate the initial observation
        observation = self._get_obs()

        # 6. Generate the initial info
        info = self._get_info()

        # For simplicity, let's assume our agent is always player 0
        # If it's not player 0's turn, simulate until it is.
        # (This is an advanced setup; for now, let's just assume
        # the agent always starts.)

        return observation, info

    def step(self, action: int):
        """
        Executes one step in the environment given an action.

        Args:
            action (int): The card ID (0-35) chosen by the agent.
        """

        # --- 1. Agent Plays Card ---
        # (Assuming it's our agent's turn)
        # ...
        # Logic to validate 'action' against legal mask (optional but good)
        # Update self.current_trick and self.player_hands[agent_id]
        # ...
        self.current_trick.append(action)
        self.current_player_idx = (self.current_player_idx + 1) % 4

        # --- 2. Simulate Opponent Moves ---
        # This is the "trick loop". It needs to run until it's
        # the agent's turn again.

        # Let's assume a 4-player game and our agent is player 0.
        # We need to simulate players 1, 2, and 3.
        for _ in range(3): # For the other 3 players
            # Get the observation for the *current* opponent
            obs_tensor = torch.tensor(
                self._get_obs(),
                dtype=torch.long,
                device=self.device
            ).unsqueeze(0)

            # Get the legal mask for the *current* opponent
            info = self._get_info()
            mask_tensor = torch.tensor(
                info["legal_mask"],
                dtype=torch.bool,
                device=self.device
            ).unsqueeze(0)

            # Use the opponent_model to choose an action
            with torch.no_grad():
                log_probs, _ = self.opponent_model(obs_tensor, mask_tensor)
                opponent_action = torch.argmax(log_probs, dim=-1).item()

            # ...
            # Logic to play the 'opponent_action'
            # Update self.current_trick, self.player_hands[opponent_id]
            # ...
            self.current_trick.append(opponent_action)
            self.current_player_idx = (self.current_player_idx + 1) % 4

        # --- 3. Resolve the Trick ---
        # ...
        # Logic to see who won self.current_trick
        # Update self.tricks_won
        # Set self.current_player_idx to the winner of the trick
        # Clear self.current_trick
        # ...

        # --- 4. Check for End of Game ---
        # (e.g., all 9 tricks have been played)
        # ...
        done = False
        if len(self.player_hands[0]) == 0: # e.g., if hands are empty
            done = True

        # --- 5. Calculate Reward ---
        reward = 0
        if done:
            # Game is over, calculate final score
            # e.g., +1 for win, -1 for loss for our agent (player 0)
            # ...
            my_team_score = ...
            other_team_score = ...
            reward = 1 if my_team_score > other_team_score else -1

        # --- 6. Prepare Next Turn's Info ---
        observation = self._get_obs()
        info = self._get_info()

        # 'terminated' is 'done'
        # 'truncated' is for time limits, which we don't have
        truncated = False

        return observation, reward, done, truncated, info