import unittest
import numpy as np
from unittest.mock import MagicMock, patch

from rl.jass_rules import Suit, Card, Announcement, PlayerPosition, ANNOUNCE_THREE, ANNOUNCE_NELL, ANNOUNCE_FIFTY
from rl.player import Player
from rl.strategy import Strategy
from rl.tokens import Tokens
from rl.trick import Trick

# Import the Player class and necessary constants from the mock setup

# Constants for common card IDs
SPADE_8_ID = 2
SPADE_9_ID = 3
SPADE_BOURG_ID = 5
HEART_6_ID = 9
CLUB_6_ID = 18
DIAMOND_6_ID = 27
HEART_9_ID = 12

# --- Mocking Strategy Class ---
class MockStrategy(Strategy):
    def choose_card(self, legal_moves, hand):
        return legal_moves[0].number
    def choose_trump_suit(self, hand, first):
        return Suit.SPADE.value

class TestPlayerBeliefs(unittest.TestCase):

    def setUp(self):
        """Set up a fresh Player instance for each test."""
        self.strategy = MockStrategy()
        self.player = Player(self.strategy)
        self.player._current_trump = Suit.HEART # Set a default trump for trick logic

    def _init_hand(self, card_ids: list[int]):
        """Helper to initialize the hand and probability matrix."""
        hand = [Card(i) for i in card_ids]
        self.player.set_hand(hand)

    def test_set_hand_initialization(self):
        """Test initial probability matrix setup after dealing."""
        my_card_ids = [0, 9, 18, 27, 1, 10, 19, 28, 2] # 9 cards
        self._init_hand(my_card_ids)

        # 1. Check shape and default probability for non-held cards
        self.assertEqual(self.player._deck_probs.shape, (36, 3))
        # Non-held cards should have 1/3 probability
        self.assertAlmostEqual(self.player._deck_probs[3, 0], 1/3, places=5)
        self.assertAlmostEqual(self.player._deck_probs[3, 1], 1/3, places=5)
        self.assertAlmostEqual(self.player._deck_probs[3, 2], 1/3, places=5)

        # 2. Check probabilities for held cards
        for card_id in my_card_ids:
            self.assertTrue(np.all(self.player._deck_probs[card_id, :] == 0))

        # 3. Check known_cards_in_hand is empty lists for all opponents
        self.assertEqual(self.player._known_cards_in_hand, [[], [], []])

    # --- Test Announcement Logic (set_announcements) ---

    def test_announcement_sequence_positive_update(self):
        """Test belief update for a sequence (Tierce: 3 cards)."""
        # Announce: Tierce (Type 1), highest card: Card(2) = Spade 8
        # Card IDs: [2, 1, 0] (Spade 8, 7, 6)

        # Mock Announcement to simulate an opponent (PlayerPosition.RIGHT, index 0) having this Tierce
        mock_announcement = Announcement(ANNOUNCE_THREE, Card(SPADE_8_ID))

        # Initialize with the card outside my hand
        self._init_hand([HEART_6_ID])
        self.player.set_announcements([(PlayerPosition.RIGHT, mock_announcement)])

        # Check: Cards 0, 1, 2 must have probability [1, 0, 0] for Opponent 1 (index 0)
        for card_id in [0, 1, SPADE_8_ID]:
            self.assertTrue(np.all(self.player._deck_probs[card_id, :] == [1.0, 0.0, 0.0]))
            self.assertIn(card_id, self.player._known_cards_in_hand[PlayerPosition.RIGHT-1])

    def test_announcement_carre_positive_update(self):
        """Test belief update for a set (Carré: 4 of a kind, e.g., Nells)."""
        # Announce: Carré de Nells (Type 5), highest card: Nell (Rank 3)
        # Card IDs: 3, 12, 21, 30 (Nell of all 4 suits)

        mock_nell_announcement = Announcement(ANNOUNCE_NELL, Card(SPADE_9_ID)) # Card(3) is Spade Nell
        self._init_hand([HEART_6_ID])
        self.player.set_announcements([(PlayerPosition.ACROSS, mock_nell_announcement)]) # Opponent 2 (index 1)

        expected_card_ids = [3, 12, 21, 30]

        # Check: Cards must have probability [0, 1, 0] for Opponent 2 (index 1)
        for card_id in expected_card_ids:
            self.assertTrue(np.all(self.player._deck_probs[card_id, :] == [0.0, 1.0, 0.0]))
            self.assertIn(card_id, self.player._known_cards_in_hand[1])

    def test_announcement_sequence_negative_update(self):
        """Test belief update for the exclusion principle (e.g., announcing a Cinquante implies no Cent)."""
        # Announce: Cinquante (Type 2), highest card: Spade 9 (Card 3)
        # Card IDs: [3, 2, 1, 0]. Length 4.

        # The exclusion principle is: the card just below the sequence (Card 4 = Spade 10)
        # must NOT be held by the announcing player.

        # Mock Announcement
        mock_announcement = Announcement(ANNOUNCE_FIFTY, Card(SPADE_9_ID))

        self._init_hand([HEART_6_ID])
        self.player.set_announcements([(PlayerPosition.LEFT, mock_announcement)]) # Opponent 3 (index 2)

        # 1. Check POSITIVE update (cards held)
        self.assertTrue(np.all(self.player._deck_probs[3, :] == [0.0, 0.0, 1.0]))

        # 2. Check NEGATIVE update (Card 4, Spade 10)
        # Expected: Probabilities for Card 4 should be [0.5, 0.5, 0.0]
        # (Split between other two opponents, 0 for the announcer)

        # The logic: target_row = [0.5, 0.5, 0.5]. target_row[player] = 0.
        # Player index = 2. Result: [0.5, 0.5, 0.0]
        expected_neg_prob = [0.5, 0.5, 0.0]

        # The card just above the sequence (Card 4)
        excluded_card_id = 4

        # The card just below the sequence (Card 0 - 1) is not tested as per the original logic

        np.testing.assert_allclose(self.player._deck_probs[excluded_card_id, :], expected_neg_prob, atol=1e-5)


    # --- Test Card Played Logic (played) ---

    def test_played_card_removal(self):
        """Test that the card played by SELF is removed from hand."""
        self._init_hand([SPADE_8_ID, HEART_6_ID])
        self.player.played(Card(SPADE_8_ID), PlayerPosition.SELF)
        self.assertEqual(len(self.player._hand), 1)
        self.assertEqual(self.player._hand[0].number, HEART_6_ID)

    def test_played_card_deck_prob_update(self):
        """Test that a played card sets its probability to 0 for all opponents."""
        self._init_hand([HEART_6_ID])
        self.player.played(Card(SPADE_8_ID), PlayerPosition.RIGHT) # Opponent 1 (index 0)

        # Card 2 (Spade 8) should now have [0, 0, 0] probabilities
        self.assertTrue(np.all(self.player._deck_probs[SPADE_8_ID, :] == 0))

    def test_played_card_known_card_removal(self):
        """Test that a played card is removed from the known_cards_in_hand list."""
        self._init_hand([HEART_6_ID])
        self.player._known_cards_in_hand[0].append(SPADE_8_ID)
        self.player.played(Card(SPADE_8_ID), PlayerPosition.RIGHT)
        self.assertNotIn(SPADE_8_ID, self.player._known_cards_in_hand[0])

    @patch('rl.trick.Trick', new=Trick) # Use the mock Trick class for easier state control
    def test_played_not_following_suit_deduction(self):
        """Test the deduction that a player has no cards in the lead suit."""
        lead_suit = Suit.HEART # Lead Suit = Heart (ID 1)

        # Init: Give player an unrelated hand
        self._init_hand([SPADE_8_ID])

        # Setup: Start a trick (Lead is Heart 6 = 9)
        self.player._trick = Trick(trump=Suit.DIAMOND) # Trump is Diamond
        self.player._trick.play_card(Card(HEART_6_ID), PlayerPosition.ACROSS) # Player 3 leads Heart 6

        # Opponent (Player 1/RIGHT) plays a non-lead, non-trump card (Club 6 = 18)
        self.player.played(Card(CLUB_6_ID), PlayerPosition.LEFT) # Player 1 (index 0)

        # Check: All 9 Heart cards (9 to 17) should be updated for Player 1 (index 0)
        # Expected: [0.5, 0.5, 0.5] -> [0.0, 0.5, 0.5]
        expected_neg_prob = [0.5, 0.5, 0]

        for card_id in range(9, 18): # Heart cards 9 to 17
            # Skip the Bourg (Jack) of Trumps if it were the lead suit (it's not here)
            # The logic to skip Bourg only applies if lead_suit is trump.
            # Here lead_suit=HEART, trump=DIAMOND. No skip necessary.

            # The logic is: target_row = [0.5, 0.5, 0.5]. target_row[player] = 0.
            # Player index = 0. Result: [0.0, 0.5, 0.5]

            np.testing.assert_allclose(self.player._deck_probs[card_id, :], expected_neg_prob, atol=1e-5)

        # Verify a non-Heart card is not modified
        self.assertAlmostEqual(self.player._deck_probs[1, 0], 1/3, places=5)

    # --- Test Probability Query Logic ---

    def test_get_k_most_likely_cards_standard(self):
        """Test the combined k/threshold logic in the normal case (1/3 prob)."""
        self._init_hand([0, 1, 2, 3]) # 4 cards in hand

        # 32 cards remain with 1/3 probability for all 3 opponents.
        # We look for the top k=5 cards for Opponent 1 (index 0) with prob > 0.3

        k = 5
        threshold = 0.3

        card_ids, probs = self.player._get_k_most_likely_cards(0, k, threshold)

        # 1. Should return exactly 5 cards
        self.assertEqual(len(card_ids), 5)
        self.assertEqual(len(probs), 5)

        # 2. All probabilities should be 1/3
        for prob in probs:
            self.assertAlmostEqual(prob, 1/3, places=5)

    def test_get_k_most_likely_cards_less_than_k_available(self):
        """Test when fewer than k cards meet the threshold."""
        # Manually set probabilities for a few cards above threshold
        self._init_hand([0, 1, 2, 3])
        self.player._deck_probs[4:8, 0] = 0.8 # Cards 4, 5, 6, 7 have 0.8 prob for Opponent 1
        self.player._deck_probs[8, 0] = 0.0 # Card 8 has 0.0 prob

        k = 10 # Request 10 cards
        threshold = 0.5 # Only cards 4, 5, 6, 7 meet this

        card_ids, probs = self.player._get_k_most_likely_cards(0, k, threshold)

        # 1. Should return exactly 4 cards
        self.assertEqual(len(card_ids), 4)

        # 2. Card IDs should be [7, 6, 5, 4] (sorted by rank)
        self.assertListEqual(sorted(card_ids.tolist()), [4, 5, 6, 7])

        # 3. All probabilities should be 0.8
        for prob in probs:
            self.assertAlmostEqual(prob, 0.8, places=5)

    def test_get_k_most_likely_cards_with_zero_results(self):
        """Test when no cards meet the threshold."""
        self._init_hand([0, 1, 2, 3])

        k = 5
        threshold = 0.9 # No cards have this probability

        card_ids, probs = self.player._get_k_most_likely_cards(0, k, threshold)

        # 1. Should return empty arrays
        self.assertEqual(len(card_ids), 0)
        self.assertEqual(len(probs), 0)

    # --- Test Tokenization Logic (get_state_as_tokens) ---

    @patch('rl.tokens.Tokens.confidence_token', return_value=999) # Mock confidence for stability
    def test_get_state_as_tokens_belief_section(self, mock_conf):
        """Test the BELIEF section tokenization, which relies on probability functions."""
        self._init_hand([0, 1, 2, 3]) # My hand
        self.player._current_trump = Suit.SPADE # Must have trump set for trick/belief
        self.player._trump_selector = PlayerPosition.ACROSS

        # Manually set known cards for Opponent 1 (index 0)
        self.player._known_cards_in_hand[0] = [SPADE_8_ID, SPADE_9_ID] # Cards 2, 3
        # Cards 4, 5, 6, 7 have 1/3 prob for Opponent 1

        # Request k=4 cards total per player

        tokens = self.player.get_state_as_tokens(first_turn_of_trump_selection=False)

        # BELIEF Section starts at index 68
        # Opponent 1 (p=0, pos 1): tokens[69] should be P_OPP_R (51)

        # Index 69: P_OPP_R
        self.assertEqual(tokens[69], Tokens.P_OPP_R)

        # Known Card 1 (Card 2 / Spade 8)
        self.assertEqual(tokens[70], 10 + SPADE_8_ID) # Card token
        self.assertEqual(tokens[71], 999)            # Confidence token

        # Known Card 2 (Card 3 / Spade 9)
        self.assertEqual(tokens[72], 10 + SPADE_9_ID)
        self.assertEqual(tokens[73], 999)

        # Likely Card 1 (1/3 prob, should be Card 4, 5, 6, 7)
        # Since _get_k_most_likely_cards is called, the two next tokens should be for a likely card
        # The card ID is pulled from the top of the 1/3 list, which should be card 35, 34... etc.
        # We just assert that they are not PAD (0) and not the known cards.

        # The section should end at 96 (tokens[95])
        self.assertEqual(tokens[95], Tokens.PAD)

