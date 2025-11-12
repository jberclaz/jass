import unittest

import torch
import legal_mask


class TestLegalMaskWithRules(unittest.TestCase):
    def test_first_to_play_all_legal(self):
        # Tokens: first play, hand with 9 cards (0-8)
        tokens = torch.tensor([0] * 95)
        tokens[10:19] = torch.tensor([10, 11, 12, 13, 14, 15, 16, 17, 18])
        tokens[21] = 0  # no leading card
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[0:9] = True  # cards 0-8 legal
        self.assertTrue(torch.equal(mask, expected))

    def test_has_suit_only_suit_legal(self):
        # Leading hearts (suit 2), hand has hearts (card 18) + clubs (0-2)
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56 + 2  # trump clubs
        tokens[10:14] = torch.tensor([10, 11, 12, 20])  # spade 0-2 + hearts 0 (28-10=18, suit 2)
        tokens[21] = 21  # leading hearts
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[10] = True  # only hearts legal
        self.assertTrue(torch.equal(mask, expected))

    def test_no_suit_all_hand_legal(self):
        # Leading hearts, no hearts in hand (only clubs 0-2)
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56 + 2  # trump clubs
        tokens[10:13] = torch.tensor([28, 29, 30])  # clubs 0-2
        tokens[21] = 20  # leading hearts
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[18:21] = True  # all hand legal
        self.assertTrue(torch.equal(mask, expected))

    def test_trump_undercut_higher_legal(self):
        # Leading clubs (0), plie has trump spades rank 5 (card 14 = suit1*9+5, token=24)
        # Hand has clubs 0 + spades rank 7 (card 16 = suit1*9+7, token=26)
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56 + 0  # trump spades (suit 0)
        tokens[10] = 10 + 18  # clubs 0
        tokens[11] = 10 + 5  # spades 7
        tokens[21] = 10 + 19  # leading clubs
        tokens[23] = 10 + 4  # plie trump rank 5
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[5] = True
        expected[18] = True  # clubs legal (follow)
        self.assertTrue(torch.equal(mask, expected))

    def test_no_undercut_lower_illegal(self):
        # Leading clubs, plie trump rank 5, hand trump rank 3 (card 12 = suit1*9+3, token=22)
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56 + 0  # trump spades
        tokens[10] = 10 + 18  # clubs
        tokens[11] = 10 + 3  # spades 3

        tokens[21] = 10 + 19  # leading clubs
        tokens[23] = 10 + 4  # plie trump rank 5
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[18] = True  # only clubs legal
        self.assertTrue(torch.equal(mask, expected))

    def test_all_trumps_higher_legal(self):
        # No non-trump cards, plie trump rank 4 (card 13 = suit1*9+4, token=23)
        # Hand trumps rank 5,6 (cards 14,15, tokens 24,25)
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56 + 0  # trump spades
        tokens[10:12] = torch.tensor([15, 16])  # spades 5,6
        tokens[21] = 10 + 19  # leading clubs
        tokens[23] = 10 + 14  # plie trump rank 4
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[5:7] = True  # cards 14,15 (suit1*9+5=14, +6=15)
        self.assertTrue(torch.equal(mask, expected))

    def test_empty_hand_no_legal(self):
        # No cards in hand
        tokens = torch.tensor([0] * 95)
        tokens[21] = 28  # leading
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        self.assertTrue(torch.all(~mask))

    def test_trump_offset(self):
        # Test trump extraction
        tokens = torch.tensor([0] * 95)
        tokens[4] = 62  # hearts trump
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        # Just check trump calc (indirect)
        self.assertEqual((tokens[4] - 60).item(), 2)  # hearts = 2

    def test_plie_loop_general(self):
        # 3 cards in plie, one trump rank 5
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56  # trump spades
        tokens[10] = 10 + 3
        tokens[21] = 10 + 18  # lead clubs
        tokens[23] = 10 + 19  # player2 clubs
        tokens[25] = 10 + 5  # player3 trump rank 5

        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[3] = True  # clubs legal
        self.assertTrue(torch.equal(mask, expected))

    def test_does_not_have_suit(self):
        tokens = torch.tensor([0] * 95)
        tokens[4] = 56  # trump spades
        tokens[10] = 10 + 10  # heart
        tokens[11] = 10 + 19  # club
        tokens[12] = 10 + 28  # diamond

        tokens[21] = 10 + 1  # lead spade
        tokens[23] = 10 + 2  # player2 spade

        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[10] = True
        expected[19] = True
        expected[28] = True
        self.assertTrue(torch.equal(mask, expected))

class TestFastLegalMask(unittest.TestCase):
    def test_legal_mask_with_batch(self):
        B, L = 128, 30
        # Tokens: 0=pad, 1-9=meta, 10-45=cards(36), 56-59=trump(4)
        tokens_batch = torch.zeros(B, L, dtype=torch.long)

        # Add trump (token 56-59)
        tokens_batch[:, 4] = torch.randint(56, 60, (B,))

        # Add hand (tokens 10-45)
        num_hand_cards = torch.randint(1, 10, (B,))
        for i in range(B):
            hand = torch.randperm(36)[:num_hand_cards[i]] + 10
            tokens_batch[i, 10:10+num_hand_cards[i]] = hand

        # Add trick data (tokens 10-45)
        # [21] = lead card, [23] = p2 card, [25] = p3 card
        trick_play_status = torch.rand(B)
        for i in range(B):
            if trick_play_status[i] > 0.1: # 90% chance it's not the first player
                tokens_batch[i, 21] = torch.randint(10, 46, (1,))
                if trick_play_status[i] > 0.4: # 60% chance p2 has played
                    tokens_batch[i, 23] = torch.randint(10, 46, (1,))
                if trick_play_status[i] > 0.7: # 30% chance p3 has played
                    tokens_batch[i, 25] = torch.randint(10, 46, (1,))

        # 2. Run the original function in a loop
        original_results = torch.stack(
            [legal_mask.get_legal_mask_with_rules(tokens_batch[i]) for i in range(B)]
        )

        # 3. Run the new batch function
        batch_results = legal_mask.get_legal_mask_with_rules_batch(tokens_batch)

        # 4. Compare
        all_correct = torch.all(original_results == batch_results).item()
        self.assertTrue(all_correct)

        print(f"Batch size: {B}")
        print(f"Original shape: {original_results.shape}")
        print(f"Batch shape:    {batch_results.shape}")
        print(f"Outputs are identical: {all_correct}")

        # Detailed check
        if not all_correct:
            print("\n--- MISMATCH FOUND ---")
            for i in range(B):
                if not torch.all(original_results[i] == batch_results[i]):
                    print(f"Mismatch at batch index {i}")
                    print(f"Tokens: {tokens_batch[i]}")
                    print(f"Loop:   {original_results[i].int()}")
                    print(f"Batch:  {batch_results[i].int()}")
                    break