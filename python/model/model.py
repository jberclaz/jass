# model.py
import torch
import torch.nn as nn
import torch.nn.functional as F

from dataset import VOCABULARY_SIZE, TOKEN_LENGTH


class JassFormer(nn.Module):
    def __init__(self, vocab_size=VOCABULARY_SIZE, seq_len=TOKEN_LENGTH, d_model=128, nhead=8, num_layers=6):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.randn(1, seq_len, d_model) * 0.02)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=512, dropout=0.1, activation="gelu", batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.policy_head = nn.Linear(d_model, 36)  # 36 cards

    def forward(self, x, legal_mask=None):
        x = self.embedding(x) + self.pos_embedding
        x = self.transformer(x)

        cls = x[:, 0]  # [CLS] token
        logits = self.policy_head(cls)  # [B, 36]

        if legal_mask is not None:
            logits = logits.masked_fill(~legal_mask, -1e9)

        return F.log_softmax(logits, dim=-1)


class JassFormerActorCritic(nn.Module):
    def __init__(self, vocab_size=VOCABULARY_SIZE, seq_len=TOKEN_LENGTH, d_model=128, nhead=8, num_layers=6):
        super().__init__()
        # --- Shared Body ---
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.randn(1, seq_len, d_model) * 0.02)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=512, dropout=0.1, activation="gelu", batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        # --- Two Heads ---
        # 1. The Actor Head (Policy)
        self.policy_head = nn.Linear(d_model, 36) # 36 cards
        # 2. The Critic Head (Value)
        self.value_head = nn.Linear(d_model, 1) # Outputs ONE number: the value

    def forward(self, x, legal_mask=None):
        # --- Shared Body ---
        x = self.embedding(x) + self.pos_embedding
        x = self.transformer(x)
        cls = x[:, 0] # [CLS] token

        # --- Two Heads ---
        # 1. Get Logits (for the Actor)
        logits = self.policy_head(cls)
        if legal_mask is not None:
            logits = logits.masked_fill(~legal_mask, -1e9)
        log_probs = F.log_softmax(logits, dim=-1)

        # 2. Get Value (for the Critic)
        value = self.value_head(cls)

        return log_probs, value.squeeze(-1) # Return both
