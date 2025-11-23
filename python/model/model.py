# model.py
import torch
import torch.nn as nn
import torch.nn.functional as F
import legal_mask as lm
from dataset import VOCABULARY_SIZE, TOKEN_LENGTH
from rl.tokens import Tokens


class JassFormer(nn.Module):
    def __init__(self, vocab_size=VOCABULARY_SIZE, seq_len=TOKEN_LENGTH, d_model=128, nhead=8, num_layers=6):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.randn(1, seq_len, d_model) * 0.02)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=4*d_model, dropout=0.1, activation="gelu", batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.policy_head = nn.Linear(d_model, 36)  # 36 cards
        self.trump_head = nn.Linear(d_model, 5)  # 4 suits + pass

    def forward(self, x, legal_mask=None, trump_legal_mask=None):
        x = self.embedding(x) + self.pos_embedding
        x = self.transformer(x)

        cls = x[:, 0]  # [CLS] token
        logits = self.policy_head(cls)  # [B, 36]
        trump_logits = self.trump_head(cls)

        if legal_mask is not None:
            logits = logits.masked_fill(~legal_mask, -1e9)
        if trump_legal_mask is not None:
            trump_logits = trump_logits.masked_fill(~trump_legal_mask, -1e9)

        return F.log_softmax(logits, dim=-1), F.log_softmax(trump_logits, dim=-1)


class JassFormerActorCritic(nn.Module):
    def __init__(self, vocab_size=VOCABULARY_SIZE, seq_len=TOKEN_LENGTH, d_model=128, nhead=8, num_layers=6):
        super().__init__()
        # --- Shared Body ---
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.randn(1, seq_len, d_model) * 0.02)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=4*d_model, dropout=0.1, activation="gelu", batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        # --- Two Heads ---
        # 1. The Actor Head (Policy)
        self.policy_head = nn.Linear(d_model, 36) # 36 cards
        self.trump_head = nn.Linear(d_model, 5)  # 4 suits + pass
        # 2. The Critic Head (Value)
        self.value_head = nn.Linear(d_model, 1) # Outputs ONE number: the value

    def forward_original(self, x, legal_mask=None, trump_legal_mask=None ):
        # --- Shared Body ---
        x = self.embedding(x) + self.pos_embedding
        x = self.transformer(x)
        cls = x[:, 0] # [CLS] token

        # --- Two Heads ---
        # 1. Get Logits (for the Actor)
        logits = self.policy_head(cls)
        trump_logits = self.trump_head(cls)

        if legal_mask is not None:
            logits = logits.masked_fill(~legal_mask, -1e9)
        if trump_legal_mask is not None:
            trump_logits = trump_logits.masked_fill(~trump_legal_mask, -1e9)
        log_probs = F.log_softmax(logits, dim=-1)
        trump_log_probs =  F.log_softmax(trump_logits, dim=-1)

        # 2. Get Value (for the Critic)
        value = self.value_head(cls)

        return log_probs, trump_log_probs, value.squeeze(-1) # Return both

    def forward(self, obs_dict: dict):
        tokens = obs_dict["obs"].to(self.device)           # [B, L]

        # --- Shared Body ---
        x = self.embedding(tokens) + self.pos_embedding
        x = self.transformer(x)
        cls = x[:, 0] # [CLS] token

        # --- Two Heads ---
        # 1. Get Logits (for the Actor)
        logits = self.policy_head(cls)
        trump_logits = self.trump_head(cls)
        # 2. Get Value (for the Critic)
        value = self.value_head(cls)

        legal_mask = lm.get_legal_mask_with_rules_batch(tokens).to(self.device)
        trump_legal_mask = lm.get_trump_mask_batch(tokens).to(self.device)

        logits = logits.masked_fill(~legal_mask, -1e9)
        trump_logits = trump_logits.masked_fill(~trump_legal_mask, -1e9)

        logits_full = torch.cat([logits, trump_logits], dim=-1)

        return logits_full, value.squeeze(-1) # Return both

    def alternate_forward(self, obs_dict: dict):
        tokens = obs_dict["obs"].to(self.device)           # [B, L]
        B = tokens.shape[0]

        # === 1. Detect current phase (100% reliable) ===
        is_trump_phase = (tokens[:, 2] == Tokens.CHOOSE_TRUMP_SUIT)   # pos 2 = 126

        # === 2. Compute both masks (cheap, vectorized) ===
        legal_mask       = lm.get_legal_mask_with_rules_batch(tokens).to(self.device)      # [B, 36]
        trump_legal_mask = lm.get_trump_mask_batch(tokens).to(self.device)                # [B, 5]

        # === 3. Shared transformer body (exactly like before) ===
        x = self.embedding(tokens) + self.pos_embedding
        x = self.transformer(x)
        cls = x[:, 0]                                                       # [B, d_model]

        # === 4. Heads ===
        card_logits   = self.policy_head(cls)        # [B, 36]  → raw logits
        trump_logits  = self.trump_head(cls)         # [B, 5]   → raw logits
        value         = self.value_head(cls).squeeze(-1)   # [B]

        # === 5. Apply masking (still safe, but we will override below) ===
        card_logits   = card_logits.masked_fill(~legal_mask,       -1e9)
        trump_logits  = trump_logits.masked_fill(~trump_legal_mask, -1e9)

        # === 6. Build unified 41-dim action space (this is the key) ===
        final_logits = torch.full((B, 41), float('-inf'), device=self.device, dtype=card_logits.dtype)

        # Card play phase → only 0–35 valid
        card_phase = ~is_trump_phase
        final_logits[card_phase, :36] = card_logits[card_phase]

        # Trump selection phase → only 36–40 valid
        final_logits[is_trump_phase, 36:41] = trump_logits[is_trump_phase]

        return final_logits, value

    @staticmethod
    def load_policy_weights(ac_model: 'JassFormerActorCritic', torch_state_file: str):
        """
        Loads policy weights from a JassFormer state dict into the JassFormerActorCritic.
        Value head weights remain randomized.
        """
        policy_state_dict = torch.load(torch_state_file, map_location="cpu")

        # Load the AC model's current state dict
        ac_state_dict = ac_model.state_dict()

        # Iterate over the provided policy weights (MC Model)
        for name, param in policy_state_dict.items():
            if name in ac_state_dict:
                # 1. Direct transfer (e.g., embedding, transformer body weights)
                ac_state_dict[name].copy_(param)

        # Load the modified state dict back into the AC model
        ac_model.load_state_dict(ac_state_dict)
        print("✅ Policy weights successfully loaded into Actor-Critic model.")
        print("   Value head initialized randomly and ready for RL training.")