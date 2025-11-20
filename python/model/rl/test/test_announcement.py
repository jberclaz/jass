import unittest

from rl.jass_rules import (
    Card, Suit, Announcement,
    RANK_6, RANK_7, RANK_8, RANK_NELL, RANK_10, RANK_BOURG, RANK_DAME, RANK_ROI, RANK_AS,
    ANNOUNCE_THREE, ANNOUNCE_FIFTY, ANNOUNCE_HUNDRED,
    ANNOUNCE_CARRE, ANNOUNCE_NELL, ANNOUNCE_BOURGS
)


# --- Helper function for test setup ---

def get_card(rank: int, suit: Suit) -> Card:
    """Helper to quickly create a Card object."""
    return Card.from_rank_color(rank, suit)

class TestAnnouncement(unittest.TestCase):
    """
    Tests for the Announcement class, focusing on finding announcements and comparison logic.
    """

    # --- Test Announcement Finding (Squares and Suits) ---

    def test_find_squares(self):
        """Test finding Carrés (Squares) based on rank."""

        # 4 Bourgs (Jacks) - Highest value square (200)
        hand_bourgs = [
            get_card(RANK_BOURG, Suit.SPADE),
            get_card(RANK_BOURG, Suit.HEART),
            get_card(RANK_BOURG, Suit.CLUB),
            get_card(RANK_BOURG, Suit.DIAMOND),
            get_card(RANK_7, Suit.SPADE)
        ]
        announcements = Announcement.find_announcements(hand_bourgs)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_BOURGS)
        self.assertEqual(announcements[0].value, 200)

        # 4 Nells (9s) - Second highest value square (150)
        hand_nells = [
            get_card(RANK_NELL, Suit.SPADE),
            get_card(RANK_NELL, Suit.HEART),
            get_card(RANK_NELL, Suit.CLUB),
            get_card(RANK_NELL, Suit.DIAMOND),
            get_card(RANK_7, Suit.SPADE)
        ]
        announcements = Announcement.find_announcements(hand_nells)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_NELL)
        self.assertEqual(announcements[0].value, 150)

        # 4 As (Aces) - Standard square (100)
        hand_as = [
            get_card(RANK_AS, Suit.SPADE),
            get_card(RANK_AS, Suit.HEART),
            get_card(RANK_AS, Suit.CLUB),
            get_card(RANK_AS, Suit.DIAMOND),
            get_card(RANK_7, Suit.SPADE)
        ]
        announcements = Announcement.find_announcements(hand_as)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_CARRE)
        self.assertEqual(announcements[0].value, 100)

        # 4 6s - Standard square (100)
        hand_sixes = [
            get_card(RANK_6, Suit.SPADE),
            get_card(RANK_6, Suit.HEART),
            get_card(RANK_6, Suit.CLUB),
            get_card(RANK_6, Suit.DIAMOND),
            get_card(RANK_7, Suit.SPADE)
        ]
        announcements = Announcement.find_announcements(hand_sixes)
        self.assertEqual(len(announcements), 0)

        # No square
        hand_no_square = [
            get_card(RANK_AS, Suit.SPADE),
            get_card(RANK_ROI, Suit.HEART),
            get_card(RANK_DAME, Suit.CLUB),
            get_card(RANK_BOURG, Suit.DIAMOND)
        ]
        announcements = Announcement.find_announcements(hand_no_square)
        self.assertEqual(len(announcements), 0)

    def test_find_suits(self):
        """Test finding Suites (Runs/Sequences) for 3, 4, and 5 cards."""

        # NOTE: The find_announcements and _find_suits methods rely on the hand
        # being sorted by Card.number() descending for the sequence check to work.
        # We must create the hand in the expected sorted order for these tests.

        # 3-Card Run (Tierce / 20 points) ending at As of Spade
        hand_three = [
            get_card(RANK_AS, Suit.SPADE), # highest
            get_card(RANK_DAME, Suit.SPADE),
            get_card(RANK_ROI, Suit.SPADE),
            get_card(RANK_7, Suit.HEART), # unrelated card
        ]
        announcements = Announcement.find_announcements(hand_three)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_THREE)
        self.assertEqual(announcements[0].value, 20)
        self.assertEqual(announcements[0].highest_card.suit, Suit.SPADE)
        self.assertEqual(announcements[0].highest_card.rank, RANK_AS)

        # 4-Card Run (Quarte / 50 points) ending at 10 of Heart
        hand_four = [
            get_card(RANK_10, Suit.HEART), # highest
            get_card(RANK_NELL, Suit.HEART),
            get_card(RANK_8, Suit.HEART),
            get_card(RANK_7, Suit.HEART),
            get_card(RANK_AS, Suit.DIAMOND), # unrelated card
        ]
        announcements = Announcement.find_announcements(hand_four)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_FIFTY)
        self.assertEqual(announcements[0].value, 50)
        self.assertEqual(announcements[0].highest_card.rank, RANK_10)

        # 5-Card Run (Quinte / 100 points) ending at Roi of Club
        hand_five = [
            get_card(RANK_ROI, Suit.CLUB), # highest
            get_card(RANK_DAME, Suit.CLUB),
            get_card(RANK_BOURG, Suit.CLUB),
            get_card(RANK_10, Suit.CLUB),
            get_card(RANK_NELL, Suit.CLUB),
        ]
        announcements = Announcement.find_announcements(hand_five)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_HUNDRED)
        self.assertEqual(announcements[0].value, 100)
        self.assertEqual(announcements[0].highest_card.rank, RANK_ROI)

        # Longer than 5 is capped at 5-card run (100 points)
        hand_six = [
            get_card(RANK_AS, Suit.DIAMOND), # highest
            get_card(RANK_ROI, Suit.DIAMOND),
            get_card(RANK_DAME, Suit.DIAMOND),
            get_card(RANK_BOURG, Suit.DIAMOND),
            get_card(RANK_10, Suit.DIAMOND),
            get_card(RANK_NELL, Suit.DIAMOND),
        ]
        announcements = Announcement.find_announcements(hand_six)
        self.assertEqual(len(announcements), 1)
        self.assertEqual(announcements[0].type, ANNOUNCE_HUNDRED)
        self.assertEqual(announcements[0].value, 100)
        self.assertEqual(announcements[0].highest_card.rank, RANK_AS)

        # Two separate runs (must ensure the index 'i' skip logic works)
        hand_two_runs = [
            get_card(RANK_AS, Suit.SPADE), # Run 1: AS-ROI-DAME (indices 0-2)
            get_card(RANK_ROI, Suit.SPADE),
            get_card(RANK_DAME, Suit.SPADE),
            get_card(RANK_7, Suit.HEART), # Unrelated card
            get_card(RANK_10, Suit.CLUB), # Run 2: 10-NELL-8 (indices 4-6)
            get_card(RANK_NELL, Suit.CLUB),
            get_card(RANK_8, Suit.CLUB),
        ]
        announcements = Announcement.find_announcements(hand_two_runs)
        self.assertEqual(len(announcements), 2)
        # The runs are found in reverse order of the list indices (from high index to low)
        # Run 2 (10-NELL-8) will be found first
        self.assertEqual(announcements[0].type, ANNOUNCE_THREE)
        self.assertEqual(announcements[0].highest_card.rank, RANK_10)
        # Run 1 (AS-ROI-DAME) will be found second
        self.assertEqual(announcements[1].type, ANNOUNCE_THREE)
        self.assertEqual(announcements[1].highest_card.rank, RANK_AS)


    # --- Test Comparison Logic (Precedence) ---

    def test_announcement_comparison(self):
        """Test the complex Jass comparison logic (value, then rank, then suit/trump)."""

        # Set up a trump suit for potential tie-breaking (though not strictly necessary
        # for these tests, which focus on value/rank)
        trump = Suit.DIAMOND

        # A: 3-card run (20 points, highest card Dame)
        announcement_a = Announcement(ANNOUNCE_THREE, get_card(RANK_DAME, Suit.SPADE))
        # B: 4-card run (50 points, highest card 10)
        announcement_b = Announcement(ANNOUNCE_FIFTY, get_card(RANK_10, Suit.HEART))
        # C: 5-card run (100 points, highest card ROI)
        announcement_c = Announcement(ANNOUNCE_HUNDRED, get_card(RANK_ROI, Suit.CLUB))
        # D: Standard Carré (100 points, highest card AS)
        announcement_d = Announcement(ANNOUNCE_CARRE, get_card(RANK_AS, Suit.SPADE))
        # E: Carré de Nells (150 points)
        announcement_e = Announcement(ANNOUNCE_NELL, get_card(RANK_NELL, Suit.DIAMOND))
        # F: Carré de Bourgs (200 points)
        announcement_f = Announcement(ANNOUNCE_BOURGS, get_card(RANK_BOURG, Suit.CLUB))

        # Test 1: Higher value wins (Rule 1)
        self.assertTrue(announcement_a < announcement_b, "20 < 50")
        self.assertTrue(announcement_b < announcement_e, "50 < 150")
        self.assertTrue(announcement_e < announcement_f, "150 < 200")

        # Test 2: Value tie-breaker: Higher value (highest_card) wins (Rule 2)

        # C: Quinte, highest Roi (Rank 7, Value 100)
        # D: Carré, highest As (Rank 8, Value 100)
        # D wins because its highest card (As, Rank 8) is higher than C's (Roi, Rank 7)
        self.assertTrue(announcement_c < announcement_d, "100-Roi < 100-As (Rank 7 < Rank 8)")

        # G: Another Quinte, highest card AS (Rank 8, Value 100)
        announcement_g = Announcement(ANNOUNCE_HUNDRED, get_card(RANK_AS, Suit.HEART))

        # C: Quinte, highest Roi (Rank 7, Value 100)
        # G: Quinte, highest As (Rank 8, Value 100)
        self.assertTrue(announcement_c < announcement_g, "100-Roi < 100-As")

        # D: Carré, highest As (Rank 8, Value 100)
        # G: Quinte, highest As (Rank 8, Value 100)
        # They should be considered equal by the `__lt__` and `__gt__` definitions
        # (they have the same value and same highest card rank).
        self.assertFalse(announcement_d < announcement_g, "100-As is not less than 100-As")
        self.assertTrue(announcement_d > announcement_g, "100-As is not greater than 100-As")
        self.assertFalse(announcement_d == announcement_g)

        # Test 3: Edge case tie in value and rank
        # H: 3-card run, highest card Dame (Rank 6, Value 20)
        announcement_h = Announcement(ANNOUNCE_THREE, get_card(RANK_DAME, Suit.DIAMOND))
        # A: 3-card run, highest card Dame (Rank 6, Value 20)
        # They are equal for precedence purposes (only the winner's announcement is counted)
        self.assertFalse(announcement_a < announcement_h)
        self.assertFalse(announcement_a > announcement_h)
        self.assertTrue(announcement_a == announcement_h)
