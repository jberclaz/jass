# test_model.py
import unittest
import torch

from dataset import TOKEN_LENGTH, VOCABULARY_SIZE
from model import JassFormer


class TestJassFormer(unittest.TestCase):
    def setUp(self):
        self.model = JassFormer(d_model=256)
        self.model.eval()

    def test_output_shape(self):
        x = torch.randint(0, VOCABULARY_SIZE, (4, TOKEN_LENGTH))
        card_log_probs, trump_log_probs = self.model(x)
        self.assertEqual(card_log_probs.shape, (4, 36))
        self.assertEqual(trump_log_probs.shape, (4, 5))

    def test_cls_token_used(self):
        # Zero everything except CLS
        x = torch.zeros(2, TOKEN_LENGTH, dtype=torch.long)
        x[:, 0] = 1  # CLS
        card_log_probs1, _ = self.model(x)
        # Change CLS embedding slightly
        with torch.no_grad():
            self.model.embedding.weight[1] += 0.1
        card_log_probs2, _ = self.model(x)
        self.assertTrue(torch.abs(card_log_probs1 - card_log_probs2).mean() > 1e-3)


if __name__ == "__main__":
    unittest.main()
