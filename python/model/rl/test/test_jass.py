# test_jass.py
import unittest
from rl.jass_rules import Card, Suit, WINNING_SCORE, RANK_BOURG, DIAMOND_SEVEN
from rl.player import Player
from rl.strategy import RandomStrategy
from rl.controller import Controller
from rl.trick import Trick


class TestJassRules(unittest.TestCase):

    def test_card_basics(self):
        c = Card(14)  # Jack of Hearts (suit=1, rank=5)
        self.assertEqual(c.suit, Suit.HEART)
        self.assertEqual(c.rank, RANK_BOURG)
        self.assertEqual(c.number, 14)

    def test_card_value_trump(self):
        card = Card(5)  # Jack of Spades
        self.assertEqual(card.get_value(Suit.SPADE), 20)   # Trump Jack = 20
        self.assertEqual(card.get_value(Suit.HEART), 2)    # Non-trump Jack = 2

        nell = Card(3)  # Nell of Spades
        self.assertEqual(nell.get_value(Suit.SPADE), 14)

    def test_compare_trumps(self):
        jack = Card(5)   # Jack Spades
        nell = Card(3)   # Nell Spades
        ace = Card(8)    # Ace Spades

        self.assertGreater(Card.compare(jack, nell, Suit.SPADE), 0)  # Jack > Nell
        self.assertGreater(Card.compare(jack, nell, Suit.HEART), 0)  # Jack > Nell
        self.assertGreater(Card.compare(jack, ace, Suit.SPADE), 0)   # Jack > Ace
        self.assertLess(Card.compare(jack, ace, Suit.HEART), 0)   # Jack > Ace
        self.assertGreater(Card.compare(nell, ace, Suit.SPADE), 0)   # Nell > Ace
        self.assertEqual(Card.compare(nell, nell, Suit.SPADE), 0)


class TestTrickLogic(unittest.TestCase):

    def setUp(self):
        self.trump = Suit.SPADE

    def test_lead_and_follow(self):
        trick = Trick(self.trump)
        trick.play_card(Card(10), 0)  # 10 of Clubs
        trick.play_card(Card(12), 1)  # Queen of Clubs
        self.assertEqual(trick.get_owner(), 1)  # Queen > 10
        self.assertFalse(trick.is_full)
        self.assertFalse(trick.is_empty)

    def test_trump_beats_suit(self):
        trick = Trick(self.trump)
        trick.play_card(Card(9), 0)  # 6 of Hearts
        self.assertFalse(trick.is_cut)
        trick.play_card(Card(0), 1)   # 6 of Spades (trump)
        trick.play_card(Card(11), 2)  # 8 of Hearts
        self.assertEqual(trick.get_owner(), 1)
        self.assertTrue(trick.is_cut)

    def test_undertrump_not_allowed_if_higher_exists(self):
        trick = Trick(self.trump)
        hand = [Card(5), Card(3)]  # Jack and Nell of Spades

        trick.play_card(Card(20), 0)  # lead non-trump
        trick.play_card(Card(0), 1)   # 6 of Spades (low trump)

        # Player 2 has Jack and Nell → cannot undertrump
        self.assertFalse(trick.can_play(Card(3), hand))  # Nell
        self.assertFalse(trick.can_play(Card(5), hand))  # Jack

    def test_bourg_sec_exception(self):
        trick = Trick(Suit.HEART)
        hand = [Card(14), Card(0)]  # Only Jack of Hearts (Bourg Sec)

        trick.play_card(Card(10), 0)  # 7 of heart lead
        # Should be allowed to not follow
        self.assertTrue(trick.can_play(Card(0), hand))

        hand.append(Card(13))
        self.assertFalse(trick.can_play(Card(0), hand))


class TestControllerFlow(unittest.TestCase):

    def setUp(self):
        strategies = [RandomStrategy() for _ in range(4)]
        self.players = [Player(s) for s in strategies]
        self.controller = Controller(self.players)

    def test_full_match_plays_without_error(self):
        self.controller.reset()
        # Force trump so we skip selection
        self.players[self.controller._current_player]._strategy = lambda h, f: Suit.SPADE

        moves = 0
        while not self.controller._game_over:
            self.controller.play_next_turn()
            moves += 1
            if moves > 1000:  # safety
                self.fail("Infinite loop detected")

        self.assertTrue(any(s >= WINNING_SCORE for s in self.controller._scores))

    def test_diamond_seven_starts(self):
        # Force Diamond Seven in player 2's hand
        import numpy as np
        np.random.seed(42)
        self.controller.reset()
        hands = self.controller._players[0]._hand + self.controller._players[1]._hand + \
                self.controller._players[2]._hand + self.controller._players[3]._hand
        self.assertIn(Card(DIAMOND_SEVEN), hands)
        # Forehand should be whoever has it
        self.assertIn(Card(DIAMOND_SEVEN), self.controller._players[self.controller._current_player]._hand)

    def test_trump_selection_pass_and_forced(self):
        self.controller.reset()
        forehand = self.controller._current_player

        # First player passes
        self.players[forehand]._strategy.choose_trump_suit = lambda h, f: Suit.NONE
        self.controller.play_next_turn()

        # Now partner must choose (cannot pass)
        self.assertNotEqual(self.controller._current_player, forehand)
        partner = self.controller._current_player
        self.players[partner]._strategy.choose_trump_suit = lambda h, f: Suit.HEART

        self.controller.play_next_turn()
        self.assertEqual(self.controller._trump_suit, Suit.HEART)
        self.assertTrue(all(p._current_trump == Suit.HEART for p in self.players))


class TestLegalMoves(unittest.TestCase):

    def test_must_follow_suit(self):
        players = [Player(RandomStrategy()) for _ in range(4)]
        controller = Controller(players)
        controller.reset()

        # Force trump and hands
        controller._trump_suit = Suit.SPADE
        for p in players:
            p.set_trump_suit(Suit.SPADE)

        # Player 0 leads with 10 of Clubs
        p0 = players[0]
        p0._hand = [Card(10), Card(11), Card(20)]  # 10, Queen Clubs + 6 Hearts
        trick = Trick(Suit.SPADE)
        trick.play_card(Card(10), 0)

        # Player 1 must follow Clubs if has it
        p1 = players[1]
        p1._hand = [Card(12), Card(0)]  # Queen Clubs + 6 Spades
        legal = []
        for c in p1._hand:
            if trick.can_play(c, p1._hand):
                legal.append(c)
        self.assertIn(Card(12), legal)   # Must play Queen of Clubs
        self.assertNotIn(Card(0), legal) # Cannot trump if has suit

