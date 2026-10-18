# train_test.py
import math
import unittest

import torch

from model import JassFormer
from rl.tokens import Tokens
from train import forward_and_loss, soft_card_targets
from dataset import TOKEN_LENGTH

NAN = float("nan")


class TestSoftTargets(unittest.TestCase):
    def test_softmax_over_scored_legal_cards(self):
        scores = torch.full((1, 36), NAN)
        scores[0, [2, 5, 9]] = torch.tensor([10.0, 0.0, 30.0])
        legal = torch.zeros(1, 36, dtype=torch.bool)
        legal[0, [2, 5]] = True  # card 9 has a score but is not legal: ignored
        targets, has_target = soft_card_targets(scores, legal, temperature=10.0)
        self.assertTrue(has_target[0])
        self.assertAlmostEqual(targets[0, 2].item(), math.e / (math.e + 1), places=5)
        self.assertAlmostEqual(targets[0, 5].item(), 1 / (math.e + 1), places=5)
        self.assertAlmostEqual(targets.sum().item(), 1.0, places=5)
        self.assertEqual(targets[0, 9].item(), 0.0)

    def test_no_target_without_scores(self):
        scores = torch.full((2, 36), NAN)
        legal = torch.ones(2, 36, dtype=torch.bool)
        targets, has_target = soft_card_targets(scores, legal, temperature=10.0)
        self.assertFalse(has_target.any())
        self.assertFalse(targets.isnan().any())
        self.assertEqual(targets.sum().item(), 0.0)


class TestLoss(unittest.TestCase):
    def setUp(self):
        torch.manual_seed(0)
        self.model = JassFormer(d_model=32, num_layers=1).eval()
        # first card of a trick, full hand: cards 0-8, all legal
        self.tokens = torch.zeros(1, TOKEN_LENGTH, dtype=torch.long)
        self.tokens[0, 0] = Tokens.CLS
        self.tokens[0, 2] = Tokens.CHOOSE_NEXT_CARD
        self.tokens[0, 11:20] = torch.tensor([Tokens.card_token(c) for c in range(9)])
        self.action = torch.tensor([3])

    def loss(self, scores, soft_weight):
        loss_sum, nll_sum, card_log_probs, *_ = forward_and_loss(self.model, self.tokens, self.action, scores, 10.0,
                                                                 soft_weight)
        return loss_sum.item(), nll_sum.item(), card_log_probs

    def test_without_scores_is_nll(self):
        loss, nll, card_log_probs = self.loss(torch.full((1, 36), NAN), soft_weight=1.0)
        self.assertAlmostEqual(loss, nll, places=5)
        self.assertAlmostEqual(nll, -card_log_probs[0, 3].item(), places=5)

    def test_soft_weight(self):
        scores = torch.full((1, 36), NAN)
        scores[0, :9] = torch.arange(9, dtype=torch.float) * 5
        hard, nll, card_log_probs = self.loss(scores, soft_weight=0.0)
        self.assertAlmostEqual(hard, nll, places=5)
        soft, _, _ = self.loss(scores, soft_weight=1.0)
        targets = torch.softmax(scores[0, :9] / 10.0, dim=-1)
        self.assertAlmostEqual(soft, -(targets * card_log_probs[0, :9]).sum().item(), places=4)
        half, _, _ = self.loss(scores, soft_weight=0.5)
        self.assertAlmostEqual(half, (soft + hard) / 2, places=4)


if __name__ == "__main__":
    unittest.main()
