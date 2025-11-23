from typing import List
import numpy as np
import torch

from legal_mask import get_trump_mask_batch, get_legal_mask_with_rules_batch
# Import your model and legal mask functions
# NOTE: Assuming the necessary imports (like VOCABULARY_SIZE) are handled
# by your project setup when using model.py and legal_mask.py
from model import JassFormerActorCritic  # Use ActorCritic for RL
from rl.jass_rules import Card
# Import necessary game structures and tokens
from rl.strategy import Strategy


class RLAgent(Strategy):
    """
    An agent that uses the JassFormer Actor-Critic model to select cards and trumps.
    """

    def __init__(self, model: JassFormerActorCritic, device: str = 'cpu'):
        # Inherit from Strategy for compatibility with Controller
        super().__init__()
        self.model = model
        self.device = device
        # Player reference is crucial for state extraction
        self.action_size = 36  # Card actions
        self.trump_action_size = 5  # 4 suits + pass

    def _get_action(self, tokens: np.ndarray, is_trump_phase: bool):
        """
        Processes tokens, runs the model, applies mask, and samples an action.
        Returns the chosen index, log_prob, and state value.
        """
        # Convert state tokens to a PyTorch tensor (batch size 1)
        state_tensor = torch.from_numpy(tokens.reshape(1, -1))

        # 1. Get the legal mask
        if is_trump_phase:
            legal_mask = get_trump_mask_batch(state_tensor).squeeze(0)
            _, log_probs_raw, value = self.model(state_tensor, trump_legal_mask=legal_mask)
        else:
            legal_mask = get_legal_mask_with_rules_batch(state_tensor).squeeze(0)
            log_probs_raw, _, value = self.model(state_tensor, legal_mask=legal_mask)

        # 2. Run the model
        # The ActorCritic model provides log_probs (policy) and value (critic)

        log_probs = log_probs_raw.squeeze(0)

        # 3. Sample an action from the policy distribution
        # The mask is already applied in the forward pass via masked_fill(-1e9)
        # We sample based on the softmax of the resulting masked logits

        # Create the categorical distribution
        # Note: torch.exp(log_softmax_output) = probabilities
        policy_dist = torch.distributions.Categorical(logits=log_probs)

        # Sample the action index
        action_index = policy_dist.sample()

        # Get the log probability of the sampled action
        log_prob = policy_dist.log_prob(action_index)

        return action_index.item(), log_prob, value.item()

    def choose_card(self, legal_moves: List[Card], hand: List[Card]):
        """
        Called during the card play phase. Returns the chosen card ID (0-35).
        """
        # Get the full state tokens from the referenced Player instance
        # NOTE: The Player class needs a way to expose the full state to the Agent,
        # e.g., by making the Player object accessible within the RLAgent.
        tokens = self._player.get_state_as_tokens(first_turn_of_trump_selection=False)

        action_index, _, _ = self._get_action(tokens, is_trump_phase=False)

        # The action_index (0-35) directly corresponds to the Card ID
        return action_index

    def choose_trump_suit(self, hand: List[Card], first: bool):
        """
        Called during the trump selection phase. Returns the chosen Suit ID (0-4).
        """
        tokens = self._player.get_state_as_tokens(first_turn_of_trump_selection=first)

        action_index, _, _ = self._get_action(tokens, is_trump_phase=True)

        # The action_index (0-4) corresponds to Suit IDs (0-3) or Pass (4)
        return action_index

        # --- Methods for Training (to be used by train.py) ---

    def get_action_data(self, tokens: List[int], is_trump_phase: bool):
        """Returns action data (index, log_prob, value) for experience collection."""
        return self._get_action(tokens, is_trump_phase)

    def evaluate(self, tokens_tensor: torch.Tensor, is_trump_phase: bool, action_tensor: torch.Tensor):
        """
        Called by the training loop to get log_probs and value for a batch of states.
        """
        tokens_tensor = tokens_tensor.to(self.device)
        action_tensor = action_tensor.to(self.device)

        if is_trump_phase:
            legal_mask = get_trump_mask_batch(tokens_tensor)
        else:
            legal_mask = get_legal_mask_with_rules_batch(tokens_tensor)

        log_probs_raw, value = self.model(tokens_tensor, legal_mask=legal_mask)

        # Calculate the log probability of the specific action that was taken
        log_probs = log_probs_raw.gather(1, action_tensor.unsqueeze(-1)).squeeze(-1)

        return log_probs, value