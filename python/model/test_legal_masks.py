import unittest

import torch
import legal_mask
from dataset import TOKEN_LENGTH


class TestLegalMaskWithRules(unittest.TestCase):
    def test_first_to_play_all_legal(self):
        # Tokens: first play, hand with 9 cards (0-8)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[11:20] = torch.tensor([10, 11, 12, 13, 14, 15, 16, 17, 18])
        tokens[23] = 0  # no leading card
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[0:9] = True  # cards 0-8 legal
        self.assertTrue(torch.equal(mask, expected))

    def test_has_suit_only_suit_legal(self):
        # Leading hearts (suit 2), hand has hearts (card 18) + clubs (0-2)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56 + 2  # trump clubs
        tokens[11:15] = torch.tensor([10, 11, 12, 20])  # spade 0-2 + hearts 0 (28-10=18, suit 2)
        tokens[22] = 21  # leading hearts
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[10] = True  # only hearts legal
        self.assertTrue(torch.equal(mask, expected))

    def test_no_suit_all_hand_legal(self):
        # Leading hearts, no hearts in hand (only clubs 0-2)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56 + 2  # trump clubs
        tokens[11:14] = torch.tensor([28, 29, 30])  # clubs 0-2
        tokens[22] = 20  # leading hearts
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[18:21] = True  # all hand legal
        self.assertTrue(torch.equal(mask, expected))

    def test_trump_undercut_higher_legal(self):
        # Leading clubs (0), plie has trump spades rank 5 (card 14 = suit1*9+5, token=24)
        # Hand has clubs 0 + spades rank 7 (card 16 = suit1*9+7, token=26)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56 + 0  # trump spades (suit 0)
        tokens[11] = 10 + 18  # clubs 0
        tokens[12] = 10 + 5  # spades 7
        tokens[22] = 10 + 19  # leading clubs
        tokens[24] = 10 + 4  # plie trump rank 5
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[5] = True
        expected[18] = True  # clubs legal (follow)
        self.assertTrue(torch.equal(mask, expected))

    def test_no_undercut_lower_illegal(self):
        # Leading clubs, plie trump rank 5, hand trump rank 3 (card 12 = suit1*9+3, token=22)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56 + 0  # trump spades
        tokens[11] = 10 + 18  # clubs
        tokens[12] = 10 + 3  # spades 3

        tokens[22] = 10 + 19  # leading clubs
        tokens[24] = 10 + 4  # plie trump rank 5
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[18] = True  # only clubs legal
        self.assertTrue(torch.equal(mask, expected))

    def test_all_trumps_higher_legal(self):
        # No non-trump cards, plie trump rank 4 (card 13 = suit1*9+4, token=23)
        # Hand trumps rank 5,6 (cards 14,15, tokens 24,25)
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56 + 0  # trump spades
        tokens[11:13] = torch.tensor([15, 16])  # spades 5,6
        tokens[22] = 10 + 19  # leading clubs
        tokens[24] = 10 + 14  # plie trump rank 4
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[5:7] = True  # cards 14,15 (suit1*9+5=14, +6=15)
        self.assertTrue(torch.equal(mask, expected))

    def test_empty_hand_no_legal(self):
        # No cards in hand
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[22] = 28  # leading
        mask = legal_mask.get_legal_mask_with_rules(tokens)
        self.assertTrue(torch.all(~mask))

    def test_plie_loop_general(self):
        # 3 cards in plie, one trump rank 5
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56  # trump spades
        tokens[11] = 10 + 3
        tokens[22] = 10 + 18  # lead clubs
        tokens[24] = 10 + 19  # player2 clubs
        tokens[27] = 10 + 5  # player3 trump rank 5

        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[3] = True  # clubs legal
        self.assertTrue(torch.equal(mask, expected))

    def test_does_not_have_suit(self):
        tokens = torch.tensor([0] * TOKEN_LENGTH)
        tokens[5] = 56  # trump spades
        tokens[11] = 10 + 10  # heart
        tokens[12] = 10 + 19  # club
        tokens[13] = 10 + 28  # diamond

        tokens[22] = 10 + 1  # lead spade
        tokens[24] = 10 + 2  # player2 spade

        mask = legal_mask.get_legal_mask_with_rules(tokens)
        expected = torch.zeros(36, dtype=torch.bool)
        expected[10] = True
        expected[19] = True
        expected[28] = True
        self.assertTrue(torch.equal(mask, expected))

class TestFastLegalMask(unittest.TestCase):
    def test_legal_mask_with_batch(self):
        B, L = 96, 30
        # Tokens: 0=pad, 1-9=meta, 10-45=cards(36), 56-59=trump(4)
        tokens_batch = torch.zeros(B, L, dtype=torch.long)
        tokens_batch[:, 2] = torch.randint(0, 2, (B,)) + 125

        # Add trump (token 56-59)
        tokens_batch[:, 5] = torch.randint(56, 60, (B,))

        # Add hand (tokens 10-45)
        num_hand_cards = torch.randint(1, 10, (B,))
        for i in range(B):
            hand = torch.randperm(36)[:num_hand_cards[i]] + 10
            tokens_batch[i, 11:11+num_hand_cards[i]] = hand

        # Add trick data (tokens 10-45)
        # [21] = lead card, [23] = p2 card, [25] = p3 card
        trick_play_status = torch.rand(B)
        for i in range(B):
            if trick_play_status[i] > 0.1: # 90% chance it's not the first player
                tokens_batch[i, 22] = torch.randint(10, 46, (1,))
                if trick_play_status[i] > 0.4: # 60% chance p2 has played
                    tokens_batch[i, 24] = torch.randint(10, 46, (1,))
                if trick_play_status[i] > 0.7: # 30% chance p3 has played
                    tokens_batch[i, 26] = torch.randint(10, 46, (1,))

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

class TestMaskForTrumpSelection(unittest.TestCase):

    def setUp(self):
        """Set up a base token tensor to be modified in each test."""
        # A base state with 100 tokens, all zeros.
        # We will modify specific indices for each test case.
        self.base_tokens = torch.zeros(96, dtype=torch.int64)

    # --- Tests for get_trump_mask_batch ---

    def test_trump_mask_phase_1_card_play(self):
        """
        Test trump mask during PHASE 1 (Card Play).
        Expected: All False, as no trump choice is being made.
        """
        tokens = self.base_tokens.clone().unsqueeze(0) # Batch size 1
        tokens[0, 2] = 125  # Phase 1: Card Play

        mask = legal_mask.get_trump_mask_batch(tokens)
        expected = torch.tensor([[False, False, False, False, False]], dtype=torch.bool)

        torch.testing.assert_close(mask, expected)

    def test_trump_mask_phase_2_turn_1(self):
        """
        Test trump mask during PHASE 2 (Trump Choice), TURN 1.
        Expected: All True (can choose any suit or pass).
        """
        tokens = self.base_tokens.clone().unsqueeze(0)
        tokens[0, 2] = 126  # Phase 2: Trump Choice
        tokens[0, 3] = 50   # Turn 1

        mask = legal_mask.get_trump_mask_batch(tokens)
        expected = torch.tensor([[True, True, True, True, True]], dtype=torch.bool)

        torch.testing.assert_close(mask, expected)

    def test_trump_mask_phase_2_turn_2(self):
        """
        Test trump mask during PHASE 2 (Trump Choice), TURN 2.
        Expected: [T, T, T, T, F] (cannot pass).
        """
        tokens = self.base_tokens.clone().unsqueeze(0)
        tokens[0, 2] = 126  # Phase 2: Trump Choice
        tokens[0, 3] = 51   # Turn 2

        mask = legal_mask.get_trump_mask_batch(tokens)
        # Assumes the 5th option is "pass"
        expected = torch.tensor([[True, True, True, True, False]], dtype=torch.bool)

        torch.testing.assert_close(mask, expected)

    def test_trump_mask_batch(self):
        """Test a batch containing all three trump mask scenarios."""
        token1 = self.base_tokens.clone()
        token1[2] = 125  # Scenario 1: Card Play

        token2 = self.base_tokens.clone()
        token2[2] = 126  # Scenario 2: Trump Choice, Turn 1
        token2[3] = 50

        token3 = self.base_tokens.clone()
        token3[2] = 126  # Scenario 3: Trump Choice, Turn 2
        token3[3] = 51

        batch_tokens = torch.stack([token1, token2, token3])
        mask = legal_mask.get_trump_mask_batch(batch_tokens)

        expected = torch.tensor([
            [False, False, False, False, False], # Result for token1
            [True, True, True, True, True],      # Result for token2
            [True, True, True, True, False]      # Result for token3
        ], dtype=torch.bool)

        torch.testing.assert_close(mask, expected)
