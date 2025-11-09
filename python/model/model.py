# model.py
import torch
import torch.nn as nn
import torch.nn.functional as F


class JassFormer(nn.Module):
    def __init__(self, vocab_size=128, seq_len=95, d_model=128, nhead=8, num_layers=6):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.zeros(1, seq_len, d_model))

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=512,
            dropout=0.1, activation='gelu', batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.policy_head = nn.Linear(d_model, 36)  # 36 cards
        self.value_head = nn.Linear(d_model, 1)  # optional

    def forward(self, x, legal_mask=None):
        # x: [B, 99]
        mask = (x == 0)  # PAD tokens
        x = self.embedding(x) + self.pos_embedding
        x = self.transformer(x, src_key_padding_mask=mask)

        cls = x[:, 0]  # [CLS] token
        logits = self.policy_head(cls)  # [B, 36]

        if legal_mask is not None:
            logits = logits.masked_fill(~legal_mask, -1e9)

        return F.log_softmax(logits, dim=-1)