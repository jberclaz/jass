import gymnasium as gym
from gymnasium import spaces
import numpy as np
import torch

# --- We must import BOTH mask functions from your new file ---
# (Assuming your new file is legal_mask_v2.py)
from legal_mask import get_legal_mask_with_rules_batch, get_trump_mask_batch
from model import JassFormerActorCritic
from rl.tokens import Tokens

# (I'm assuming these are in your project)
# from model import JassFormerActorCritic
# from dataset import TOKEN_LENGTH, VOCABULARY_SIZE
# from rl.utils import DIAMOND_SEVEN

# --- Mock imports for a self-contained file ---
# (Remove these in your real project)
TOKEN_LENGTH = 100
VOCABULARY_SIZE = 127
DIAMOND_SEVEN = 10 # Card ID 10 (Diamond 6, assuming 0-indexed)


# --- Define constants for action space ---
CARD_ACTION_SPACE_SIZE = 36
TRUMP_ACTION_SPACE_SIZE = 5
TOTAL_ACTION_SPACE_SIZE = CARD_ACTION_SPACE_SIZE + TRUMP_ACTION_SPACE_SIZE # 41
AGENT_ID = 0
PARTNER_ID = 2

class JassEnv(gym.Env):
    """
    A two-phase (trump + card) Gymnasium environment for Jass.

    Action Space (Discrete(41)):
    - 0-35: Play a card (ID 0 to 35)
    - 36-40: Choose trump (ID 0 to 4, e.g., 4 = pass)
    """
    metadata = {"render_modes": [], "render_fps": 1}

    def __init__(self, opponent_model: JassFormerActorCritic, device):
        super().__init__()

        self.opponent_model = opponent_model
        self.device = device
        self.opponent_model.to(self.device)
        self.opponent_model.eval()

        # --- 1. Define Action Space ---
        # We combine both action spaces into one.
        # The legal_mask will tell the agent which part to use.
        # Actions 0-35 are cards. Actions 36-40 are trump choices.
        self.action_space = spaces.Discrete(TOTAL_ACTION_SPACE_SIZE)

        # --- 2. Define Observation Space ---
        # This remains your token array
        self.observation_space = spaces.Box(
            low=0,
            high=VOCABULARY_SIZE - 1,
            shape=(TOKEN_LENGTH,),
            dtype=np.int64
        )

        # --- 3. Internal Game State ---
        self.deck = np.arange(36)
        self.player_hands = np.zeros((4, 9), dtype=np.int64)
        self.current_trick = [] # Will store (player_id, card_id) tuples
        self.tricks_played = 0
        self.team_scores = np.zeros(2, dtype=np.int32)
        self.team_game_scores = np.zeros(2, dtype=np.int32)

        self.current_player_idx = 0
        self.trick_starter_idx = 0
        self.trump_suit = 0
        self.trump_chosen_on_first_turn = None
        self.position_who_chose_trump = 0

        # --- NEW: State machine variables ---
        self.game_phase = "trump"  # "trump" or "card"
        self.trump_turn = 0        # 0 (first) or 1 (second)
        self.game_state_tokens = np.zeros(TOKEN_LENGTH, dtype=np.int64)

    def _get_obs(self):
        """
        Generates the observation (token array) from the perspective
        of the self.current_player_idx.
        This logic is based on the Java GameView.java implementation.
        """
        self.game_state_tokens.fill(Tokens.PAD)
        cp = self.current_player_idx  # Current Player index (0-3)

        # === 0: CLS ===
        self.game_state_tokens[0] = Tokens.CLS

        # === 1-9: GLOBALS (9 tokens) ===
        self.game_state_tokens[1] = Tokens.SECTION_GLOBALS

        # Get relative scores
        our_game_score = self.team_game_scores[cp % 2]
        opp_game_score = self.team_game_scores[(cp + 1) % 2]
        our_match_score = self.team_scores[cp % 2]
        opp_match_score = self.team_scores[(cp + 1) % 2]

        if self.game_phase == "trump":
            # --- Trump Choice Phase Globals ---
            self.game_state_tokens[2] = Tokens.CHOOSE_TRUMP_SUIT
            self.game_state_tokens[3] = Tokens.TRUMP_FIRST_CHOICE if self.trump_turn == 0 else Tokens.TRUMP_FORCED
            self.game_state_tokens[4] = Tokens.position_token(0)  # Position is "SELF"
            self.game_state_tokens[5] = Tokens.PAD  # Trump suit is not set
        else:
            # --- Card Play Phase Globals ---
            self.game_state_tokens[2] = Tokens.CHOOSE_NEXT_CARD
            self.game_state_tokens[
                3] = Tokens.TRUMP_FIRST_CHOICE if self.trump_chosen_on_first_turn else Tokens.TRUMP_FORCED
            self.game_state_tokens[4] = Tokens.position_token(self.position_who_chose_trump)
            self.game_state_tokens[5] = Tokens.trump_token(self.trump_suit_token)

        self.game_state_tokens[6] = Tokens.game_score_token(our_game_score)
        self.game_state_tokens[7] = Tokens.game_score_token(opp_game_score)
        self.game_state_tokens[8] = Tokens.match_score_token(our_match_score)
        self.game_state_tokens[9] = Tokens.match_score_token(opp_match_score)

        # === 10-19: HAND (10 tokens) ===
        self.game_state_tokens[10] = Tokens.SECTION_HAND

        # Get and sort the current player's hand
        hand = self.player_hands[cp]
        valid_cards = hand[hand != -1]  # Filter out played cards (-1)
        sorted_hand = np.sort(valid_cards)

        for i in range(9):
            if i < len(sorted_hand):
                self.game_state_tokens[11 + i] = Tokens.card_token(sorted_hand[i])
            else:
                self.game_state_tokens[11 + i] = Tokens.PAD

        # === 20-26: CURRENT TRICK (7 tokens) ===
        self.game_state_tokens[20] = Tokens.SECTION_TRICK

        if self.game_phase == "trump":
            self.game_state_tokens[27] = Tokens.SECTION_HISTORY
            self.game_state_tokens[68] = Tokens.SECTION_BELIEF
            return self.game_state_tokens

        token_idx = 21
        for (player_id, card_id) in self.current_trick:
            if token_idx < 27:
                self.game_state_tokens[token_idx] = Tokens.position_token(player_id, cp)
                self.game_state_tokens[token_idx + 1] = Tokens.card_token(card_id)
                token_idx += 2
        # (Rest are already padded)

        # === 27-67: HISTORY (41 tokens) ===
        # (8 tricks * 5 tokens/trick + 1 section token)
        self.game_state_tokens[27] = Tokens.SECTION_HISTORY

        token_idx = 28
        # Iterate history, most recent trick first (Java `lastCompletedTricks.add`)
        for (cards_in_play_order, winner_id) in reversed(self.last_completed_tricks):
            if token_idx > 67: break  # Max 8 tricks

            for card_id in cards_in_play_order:
                self.game_state_tokens[token_idx] = Tokens.card_token(card_id)
                token_idx += 1

            self.game_state_tokens[token_idx] = Tokens.win_token(winner_id, cp)
            token_idx += 1
        # (Rest are already padded)

        # === 68-95: BELIEF / KNOWN HANDS (28 tokens) ===
        # (1 section + 3 opponents * (1 pos + 4 * 2 card/conf))
        self.game_state_tokens[68] = Tokens.SECTION_BELIEF

        token_idx = 69
        # Loop through opponents (e.g., Left, Partner, Right)
        for i in range(1, 4):
            opp_id = (cp + i) % 4

            # Add opponent position token
            self.game_state_tokens[token_idx] = Tokens.position_token(opp_id, cp)
            token_idx += 1

            # Get opponent's unplayed cards
            opp_hand = self.player_hands[opp_id]
            unplayed_cards = opp_hand[opp_hand != -1]

            # Add up to 4 known cards with 1.0 confidence
            for j in range(4):
                if j < len(unplayed_cards):
                    self.game_state_tokens[token_idx] = Tokens.card_token(unplayed_cards[j])
                    self.game_state_tokens[token_idx + 1] = Tokens.confidence_token(1.0)
                else:
                    self.game_state_tokens[token_idx] = Tokens.PAD
                    self.game_state_tokens[token_idx + 1] = Tokens.PAD
                token_idx += 2
        # (Rest are already padded)

        assert token_idx == 96, f"Token generation ended at index {token_idx}, expected 96"

        return self.game_state_tokens

    def _get_info(self):
        """
        Generates auxiliary info, primarily the combined legal mask.
        """
        # We need to get masks for the *current* player's state
        current_tokens_tensor = torch.tensor(
            self.game_state_tokens,
            dtype=torch.long,
            device=self.device
        ).unsqueeze(0) # Add batch dim [1, L]

        with torch.no_grad():
            # Get *both* masks
            legal_card_mask_batch = get_legal_mask_with_rules_batch(current_tokens_tensor) # [1, 36]
            legal_trump_mask_batch = get_trump_mask_batch(current_tokens_tensor)       # [1, 5]

            # Combine them into one 41-element mask
            combined_mask_batch = torch.cat(
                (legal_card_mask_batch, legal_trump_mask_batch),
                dim=1
            ) # [1, 41]

            # Squeeze to [41] and move to CPU
            legal_mask = combined_mask_batch.squeeze(0).cpu().numpy()

        return {"legal_mask": legal_mask}

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)

        # 1. Create and shuffle the deck
        np.random.shuffle(self.deck)

        # 2. Deal hands (reshape into 4x9)
        self.player_hands = self.deck.reshape((4, 9)).astype(np.int64)

        # 3. Find starting player (e.g., holder of DIAMOND_SEVEN)
        # np.where returns (array([player_idx]), array([card_idx]))
        start_player_tuple = np.where(self.player_hands == DIAMOND_SEVEN)
        self.current_player_idx = start_player_tuple[0][0]

        # 4. Initialize all other game state variables
        self.current_trick = []
        self.tricks_played = 0
        self.team_scores.fill(0)
        self.trick_starter_idx = self.current_player_idx
        self.trump_suit_token = 0 # 0 = not set

        # 5. Set the state machine to the beginning
        self.game_phase = "trump"
        self.trump_turn = 0
        self.trump_chosen_on_first_turn = None

        # 6. Simulate opponent turns *until* it's the agent's turn
        self._simulate_opponent_turns()

        # 7. Generate the initial observation and info for the agent
        observation = self._get_obs()
        info = self._get_info()

        return observation, info

    def step(self, action: int):
        """
        Executes one agent action, then simulates all opponent actions
        until it is the agent's turn again.
        """

        # --- 1. Agent takes the provided action ---
        # The 'action' is an int from 0-40. We must check the phase
        # to know what this action means.

        if self.game_phase == "trump":
            # Action 36-40 corresponds to trump choice 0-4
            trump_choice = action - CARD_ACTION_SPACE_SIZE
            self._handle_trump_choice(AGENT_ID, trump_choice)
        else:
            # Action 0-35 corresponds to card choice 0-35
            card_choice = action
            self._handle_card_play(AGENT_ID, card_choice)

        # --- 2. Simulate Opponent Moves ---
        # This loop runs until it's the agent's turn again (or game over)
        terminated = self._simulate_opponent_turns()

        # --- 3. Prepare Return Values ---
        observation = self._get_obs()
        info = self._get_info()

        # 4. Calculate Reward
        reward = 0
        if terminated:
            # Game is over, calculate final score
            my_team_score = self.team_scores[AGENT_ID % 2]
            opp_team_score = self.team_scores[(AGENT_ID + 1) % 2]

            # Simple win/loss reward
            reward = 1 if my_team_score > opp_team_score else -1

        truncated = False # We don't have a time limit

        return observation, reward, terminated, truncated, info

    def _simulate_opponent_turns(self):
        """
        Runs the game loop for opponents until it's AGENT_ID's turn.
        Returns True if the game ended.
        """
        # Loop while it's not the agent's turn AND the game isn't over
        while self.current_player_idx != AGENT_ID:

            # Get obs and mask for the *current opponent*
            obs_tensor = torch.tensor(
                self._get_obs(), dtype=torch.long, device=self.device
            ).unsqueeze(0)

            info = self._get_info()
            mask_tensor = torch.tensor(
                info["legal_mask"], dtype=torch.bool, device=self.device
            ).unsqueeze(0)

            # Split the mask back up for the model
            card_mask = mask_tensor[:, :CARD_ACTION_SPACE_SIZE]
            trump_mask = mask_tensor[:, CARD_ACTION_SPACE_SIZE:]

            # Use the opponent_model to choose an action
            with torch.no_grad():
                log_probs_card, log_probs_trump = self.opponent_model(
                    obs_tensor, card_mask, trump_mask
                )

            # Take the action based on the current phase
            if self.game_phase == "trump":
                # Opponent chooses trump
                opponent_action = torch.argmax(log_probs_trump, dim=-1).item()
                self._handle_trump_choice(self.current_player_idx, opponent_action)
            else:
                # Opponent plays a card
                opponent_action = torch.argmax(log_probs_card, dim=-1).item()
                self._handle_card_play(self.current_player_idx, opponent_action)

            # Check if the game just ended (e.g., in _handle_card_play)
            if self.tricks_played == 9:
                return True # Game is over

        return False # Game is not over, it's agent's turn

    def _handle_trump_choice(self, player_id: int, choice: int):
        """Processes a single trump choice (0-4)."""

        self.trump_turn += 1

        if choice == 4: # 4 is the "pass" action
            # Player passed. It's the next player's turn.
            self.current_player_idx = (self.current_player_idx + 1) % 4
            # If we've gone all the way around, it's the second turn
            if self.current_player_idx == self.trick_starter_idx:
                self.trump_turn = 1
        else:
            # --- Trump was chosen! ---
            # TODO: Convert choice (0-3) to your trump suit token (e.g., 56-59)
            self.trump_suit_token = choice + 56
            self.trump_chosen_on_first_turn = (self.trump_turn <= 4)

            # Set game phase to card play
            self.game_phase = "card"

            # The player who chose trump starts the first trick
            self.current_player_idx = player_id
            self.trick_starter_idx = player_id

    def _handle_card_play(self, player_id: int, card: int):
        """Processes a single card play (0-35)."""

        # 1. Add card to trick
        self.current_trick.append((player_id, card))

        # 2. Remove card from player's hand (e.g., mark as -1)
        hand = self.player_hands[player_id]
        card_index = np.where(hand == card)[0][0]
        hand[card_index] = -1 # Mark as played

        # 3. Advance to next player
        self.current_player_idx = (self.current_player_idx + 1) % 4

        # 4. Check if trick is over
        if len(self.current_trick) == 4:
            self._resolve_trick()

    def _resolve_trick(self):
        """
        Resolves the current trick, assigns score, and sets next player.

        !!! CRITICAL TODO !!!
        This is the core Jass trick-taking logic.
        """

        # TODO: Implement your Jass trick logic here
        # 1. Look at self.current_trick and self.trump_suit_token
        # 2. Determine who won
        winner_id = self.current_trick[0][0] # Placeholder: first player wins
        # 3. Calculate the score of the trick
        trick_score = 10 # Placeholder

        # 4. Update game state
        self.team_scores[winner_id % 2] += trick_score
        self.tricks_played += 1

        # 5. Set next player
        self.current_player_idx = winner_id
        self.trick_starter_idx = winner_id

        # 6. Clear trick
        self.current_trick = []

        # (Game-over check is handled in the simulation loop)