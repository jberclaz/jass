import unittest
import numpy as np
from rl.jass_rules import Card, Suit, PlayerPosition
from rl.player import Player
from rl.strategy import RandomStrategy
from rl.tokens import Tokens

class TestTokenGeneration(unittest.TestCase):
    def setUp(self):
        self.player = Player(RandomStrategy())
        self.player.set_hand(list(Card(i) for i in range(9)))
        self.player.set_trump_suit(Suit.SPADE, PlayerPosition.RIGHT, True)
        self.player._scores = [100, 50]
        self.player._game_scores= [20, 20]

    def test_initial_state(self):
        tokens = self.player.get_state_as_tokens(True)

        self.assertEqual(96, len(tokens))
        self.assertEqual(Tokens.CLS, tokens[0])
        self.assertEqual(Tokens.SECTION_GLOBALS, tokens[1])
        self.assertEqual(Tokens.CHOOSE_NEXT_CARD, tokens[2])
        self.assertEqual(Tokens.TRUMP_FIRST_CHOICE, tokens[3])
        self.assertEqual(Tokens.P_OPP_R, tokens[4])
        self.assertEqual(Tokens.trump_token(Suit.SPADE), tokens[5])
        self.assertEqual(Tokens.game_score_token(20), tokens[6])
        self.assertEqual(Tokens.game_score_token(20), tokens[7])
        self.assertEqual(Tokens.match_score_token(100), tokens[8])
        self.assertEqual(Tokens.match_score_token(50), tokens[9])
        self.assertEqual(Tokens.SECTION_HAND, tokens[10])
        for i in range(9):
            self.assertEqual(Tokens.card_token(i), tokens[11+i])
        self.assertEqual(Tokens.SECTION_TRICK, tokens[20])
        for i in range(21, 27):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.SECTION_HISTORY, tokens[27])
        for i in range(28, 68):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.SECTION_BELIEF, tokens[68])
        self.assertEqual(Tokens.P_OPP_R, tokens[69])
        for i in range(70, 78):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.P_PARTNER, tokens[78])
        for i in range(79, 87):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.P_OPP_L, tokens[87])
        for i in range(88, 96):
            self.assertEqual(Tokens.PAD, tokens[i])

    def test_partial_hand(self):
        for i in range(4):
            self.player.played(Card(i), PlayerPosition.SELF)
        tokens = self.player.get_state_as_tokens(True)
        self.assertEqual(Tokens.SECTION_HAND, tokens[10])
        for i in range(5):
            self.assertEqual(Tokens.card_token(i+4), tokens[11+i])
        for i in range(16, 20):
            self.assertEqual(Tokens.PAD, tokens[i])

    def test_current_trick(self):
        self.player.played(Card(10), PlayerPosition.ACROSS)
        self.player.played(Card(11), PlayerPosition.LEFT)
        tokens = self.player.get_state_as_tokens(True)
        self.assertEqual(Tokens.SECTION_TRICK, tokens[20])
        self.assertEqual(Tokens.P_PARTNER, tokens[21])
        self.assertEqual(Tokens.card_token(10), tokens[22])
        self.assertEqual(Tokens.P_OPP_L, tokens[23])
        self.assertEqual(Tokens.card_token(11), tokens[24])
        self.assertEqual(Tokens.PAD, tokens[25])
        self.assertEqual(Tokens.PAD, tokens[26])

    def test_history(self):
        for idx, c in enumerate([2, 12, 23, 18, 3, 24, 33, 15, 4]):
            self.player.played(Card(c), PlayerPosition(idx % 4))

        tokens = self.player.get_state_as_tokens(True)
        self.assertEqual(Tokens.SECTION_HISTORY, tokens[27])
        self.assertEqual(Tokens.card_token(2), tokens[28])
        self.assertEqual(Tokens.card_token(12), tokens[29])
        self.assertEqual(Tokens.card_token(23), tokens[30])
        self.assertEqual(Tokens.card_token(18), tokens[31])
        self.assertEqual(Tokens.WIN_OUR_TEAM, tokens[32])
        self.assertEqual(Tokens.card_token(3), tokens[33])
        self.assertEqual(Tokens.card_token(24), tokens[34])
        self.assertEqual(Tokens.card_token(33), tokens[35])
        self.assertEqual(Tokens.card_token(15), tokens[36])
        self.assertEqual(Tokens.WIN_OUR_TEAM, tokens[32])

    def test_belief(self):
        self.player._deck_probs[30,:] = np.array([0, 1, 0])
        self.player._deck_probs[31, :] = np.array([0, 1, 0])
        self.player._deck_probs[32, :] = np.array([0, 0, 1])

        tokens = self.player.get_state_as_tokens(True)
        self.assertEqual(Tokens.SECTION_BELIEF, tokens[68])
        self.assertEqual(Tokens.P_OPP_R, tokens[69])
        for i in range(70, 78):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.P_PARTNER, tokens[78])
        self.assertEqual(Tokens.card_token(31), tokens[79])
        self.assertEqual(Tokens.confidence_token(1), tokens[80])
        self.assertEqual(Tokens.card_token(30), tokens[81])
        self.assertEqual(Tokens.confidence_token(1), tokens[82])
        for i in range(83, 87):
            self.assertEqual(Tokens.PAD, tokens[i])
        self.assertEqual(Tokens.P_OPP_L, tokens[87])
        self.assertEqual(Tokens.card_token(32), tokens[88])
        self.assertEqual(Tokens.confidence_token(1), tokens[89])
        for i in range(90, 96):
            self.assertEqual(Tokens.PAD, tokens[i])