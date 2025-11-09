# test_model.py
import unittest
import torch
import torch.nn.functional as F

from dataset import TOKEN_LENGTH, VOCABULARY_SIZE
from model import JassFormer

class TestJassFormer(unittest.TestCase):
    def setUp(self):
        self.model = JassFormer()
        self.model.eval()

    def test_output_shape(self):
        x = torch.randint(0, VOCABULARY_SIZE, (4, TOKEN_LENGTH))
        logits = self.model(x)
        self.assertEqual(logits.shape, (4, 36))

    def test_cls_token_used(self):
        # Zero everything except CLS
        x = torch.zeros(2, TOKEN_LENGTH, dtype=torch.long)
        x[:, 0] = 1  # CLS
        logits1 = self.model(x)
        # Change CLS embedding slightly
        with torch.no_grad():
            self.model.embedding.weight[1] += 0.1
        logits2 = self.model(x)
        self.assertTrue(torch.abs(logits1 - logits2).mean() > 1e-3)
