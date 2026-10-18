package com.leflat.jass.server;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.PlayerPosition;
import com.leflat.jass.common.Plie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the GameView class, focusing on encodeStateForTransformer.
 * We use Mockito to mock dependencies from com.leflat.jass.common.
 */
@ExtendWith(MockitoExtension.class)
public class GameViewEncodeTests {

    private GameView gameView;
    private List<Card> ownHand;
    private Map<Integer, PlayerPosition> positionsMap;

    // Mock players
    @Mock
    private BasePlayer player0; // Self (ID 10, Pos 0)
    @Mock
    private BasePlayer player1; // OppR (ID 11, Pos 1)
    @Mock
    private BasePlayer player2; // Partner (ID 12, Pos 2)
    @Mock
    private BasePlayer player3; // OppL (ID 13, Pos 3)

    // Mock tricks
    @Mock
    private Plie trick1;
    @Mock
    private Plie trick2;

    @BeforeEach
    void setUp() {
        gameView = new GameView();
        ownHand = new ArrayList<>();

        // Create a stable mapping of player IDs to relative positions
        // 0: Self, 1: OppR, 2: Partner, 3: OppL
        positionsMap = new HashMap<>();
        positionsMap.put(10, PlayerPosition.SELF); // Self
        positionsMap.put(11, PlayerPosition.RIGHT); // OppR
        positionsMap.put(12, PlayerPosition.ACROSS); // Partner
        positionsMap.put(13, PlayerPosition.LEFT); // OppL

        // A common setup: 9 cards in hand, at start of game
        for (int i = 0; i < 9; i++) {
            ownHand.add(new Card(i)); // Cards 0 through 8
        }
        gameView.reset(ownHand);

        // Set a default trump and score for basic tests
        Card.atout = Card.COLOR_SPADE; // Assuming 1 = SPADES
        gameView.setTrump(PlayerPosition.RIGHT, true); // OppR chose first
        gameView.updateMatchScore(100, 50); // 100-50
        gameView.addAnnouncementScore(20, true); // Our game score = 20
        gameView.addAnnouncementScore(50, false); // Opp game score = 50
    }

    /**
     * Helper to convert byte array to int array for easier assertion.
     */
    private int[] toIntArray(byte[] bytes) {
        int[] ints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            ints[i] = bytes[i] & 0xFF; // Convert unsigned byte to int
        }
        return ints;
    }

    @Test
    void testEncodeState_InitialState() {
        // Arrange (Setup is already initial state)

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        // Total size
        assertEquals(96, tokens.length, "Token array should have 95 elements");

        // CLS token
        assertEquals(Tokens.CLS, tokens[0]);

        // === 1-8: GLOBALS ===
        assertEquals(Tokens.SECTION_GLOBALS, tokens[1]);
        assertEquals(Tokens.CHOOSE_NEXT_CARD, tokens[2]);
        assertEquals(Tokens.TRUMP_FIRST_CHOICE, tokens[3]); // Trump type
        assertEquals(Tokens.P_OPP_R, tokens[4]);            // Trump choser (Pos 1)
        assertEquals(Tokens.trumpToken(Card.COLOR_SPADE), tokens[5]); // Trump color
        assertEquals(Tokens.scoreToken(20), tokens[6]);     // Our game score
        assertEquals(Tokens.scoreToken(50), tokens[7]);     // Opp game score
        assertEquals(Tokens.globalScoreToken(100), tokens[8]); // Our match score
        assertEquals(Tokens.globalScoreToken(50), tokens[9]);  // Opp match score

        // === 9-18: HAND ===
        assertEquals(Tokens.SECTION_HAND, tokens[10]);
        for (int i = 0; i < 9; i++) {
            assertEquals(Tokens.cardToken(i), tokens[11 + i], "Hand card " + i);
        }

        // === 19-25: CURRENT TRICK ===
        assertEquals(Tokens.SECTION_TRICK, tokens[20]);
        for (int i = 21; i <= 26; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Trick token " + i + " should be PAD");
        }

        // === 26-66: HISTORY ===
        assertEquals(Tokens.SECTION_HISTORY, tokens[27]);
        for (int i = 28; i <= 67; i++) {
            assertEquals(Tokens.PAD, tokens[i], "History token " + i + " should be PAD");
        }

        // === 67-94: BELIEF ===
        assertEquals(Tokens.SECTION_BELIEF, tokens[68]);
        // Pos 1 (OppR)
        assertEquals(Tokens.P_OPP_R, tokens[69]);
        for (int i = 70; i <= 77; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Belief OppR token " + i + " should be PAD");
        }
        // Pos 2 (Partner)
        assertEquals(Tokens.P_PARTNER, tokens[78]);
        for (int i = 79; i <= 86; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Belief Partner token " + i + " should be PAD");
        }
        // Pos 3 (OppL)
        assertEquals(Tokens.P_OPP_L, tokens[87]);
        for (int i = 88; i <= 95; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Belief OppL token " + i + " should be PAD");
        }
    }

    @Test
    void testEncodeState_PartialHand() {
        // Arrange
        for (int i=0; i<4; i++) {
            gameView.cardPlayed(PlayerPosition.SELF, new Card(i));
        }

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        assertEquals(Tokens.SECTION_HAND, tokens[10]);
        assertEquals(Tokens.cardToken(4), tokens[11]);  // Sorted hand
        assertEquals(Tokens.cardToken(5), tokens[12]);
        assertEquals(Tokens.cardToken(6), tokens[13]);
        assertEquals(Tokens.cardToken(7), tokens[14]);
        assertEquals(Tokens.cardToken(8), tokens[15]);
        for (int i = 16; i <= 19; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Hand padding token " + i + " should be PAD");
        }
    }

    @Test
    void testEncodeState_CurrentTrick() {
        // Arrange: OppR (Pos 1) played Card 10, Partner (Pos 2) played Card 11
        // It is our (Pos 0) turn to play.
        gameView.cardPlayed(PlayerPosition.RIGHT, new Card(10)); // OppR
        gameView.cardPlayed(PlayerPosition.ACROSS, new Card(11)); // Partner

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        assertEquals(Tokens.SECTION_TRICK, tokens[20]);
        // Card 1 (Pos 1, Card 10)
        assertEquals(Tokens.P_OPP_R, tokens[21]);
        assertEquals(Tokens.cardToken(10), tokens[22]);
        // Card 2 (Pos 2, Card 11)
        assertEquals(Tokens.P_PARTNER, tokens[23]);
        assertEquals(Tokens.cardToken(11), tokens[24]);
        // Padding
        assertEquals(Tokens.PAD, tokens[25]);
        assertEquals(Tokens.PAD, tokens[26]);
    }

    @Test
    void testEncodeState_History() {
        for (int i=12; i>=9; i--) {
            gameView.cardPlayed(PlayerPosition.fromCode(i % 4), new Card(i));
        }
        for (int i=13; i<17; i++) {
            gameView.cardPlayed(PlayerPosition.fromCode((i-1) % 4), new Card(i));
        }

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        assertEquals(Tokens.SECTION_HISTORY, tokens[27]);
        // Trick 1
        assertEquals(Tokens.cardToken(12), tokens[28]);
        assertEquals(Tokens.cardToken(11), tokens[29]);
        assertEquals(Tokens.cardToken(10), tokens[30]);
        assertEquals(Tokens.cardToken(9), tokens[31]);
        assertEquals(Tokens.WIN_OUR_TEAM, tokens[32]); // Won by Partner (Pos 2)
        // Trick 2
        assertEquals(Tokens.cardToken(13), tokens[33]);
        assertEquals(Tokens.cardToken(14), tokens[34]);
        assertEquals(Tokens.cardToken(15), tokens[35]);
        assertEquals(Tokens.cardToken(16), tokens[36]);
        assertEquals(Tokens.WIN_OPP_TEAM, tokens[37]); // Won by OppL (Pos 3)
        // Padding
        for (int i = 38; i <= 67; i++) {
            assertEquals(Tokens.PAD, tokens[i], "History padding token " + i + " should be PAD");
        }
    }

    @Test
    void testEncodeState_BeliefKnownCards() {
        // Arrange
        gameView.playerHasCard(PlayerPosition.ACROSS, new Card(30)); // Partner (Pos 2, idx 1) has Card 30
        gameView.playerHasCard(PlayerPosition.ACROSS, new Card(31)); // Partner (Pos 2, idx 1) has Card 31
        gameView.playerHasCard(PlayerPosition.LEFT, new Card(32)); // OppL (Pos 3, idx 2) has Card 32

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        assertEquals(Tokens.SECTION_BELIEF, tokens[68]);
        // Pos 1 (OppR, idx 0) - No known cards
        assertEquals(Tokens.P_OPP_R, tokens[69]);
        for (int i = 70; i <= 77; i++) {
            assertEquals(Tokens.PAD, tokens[i], "Belief OppR token " + i + " should be PAD");
        }
        // Pos 2 (Partner, idx 1) - Has 2 known cards
        assertEquals(Tokens.P_PARTNER, tokens[78]);
        assertEquals(Tokens.cardToken(30), tokens[79]);
        assertEquals(Tokens.confidenceToken(1.0), tokens[80]);
        assertEquals(Tokens.cardToken(31), tokens[81]);
        assertEquals(Tokens.confidenceToken(1.0), tokens[82]);
        assertEquals(Tokens.PAD, tokens[83]); // Padding
        assertEquals(Tokens.PAD, tokens[84]);
        assertEquals(Tokens.PAD, tokens[85]);
        assertEquals(Tokens.PAD, tokens[86]);
        // Pos 3 (OppL, idx 2) - Has 1 known card
        assertEquals(Tokens.P_OPP_L, tokens[87]);
        assertEquals(Tokens.cardToken(32), tokens[88]);
        assertEquals(Tokens.confidenceToken(1.0), tokens[89]);
        assertEquals(Tokens.PAD, tokens[90]); // Padding
        assertEquals(Tokens.PAD, tokens[91]);
        // ... rest are pads
    }

    @Test
    void testEncodeState_BeliefProbableCards() {
        // Arrange: Manipulate probabilities
        // Card 20: OppR (idx 0) doesn't have it. Prob -> [0, 0.5, 0.5]
        gameView.playerDoesNotHaveCard(PlayerPosition.RIGHT, new Card(20));
        // Card 21: OppR (idx 0) doesn't have it. Prob -> [0, 0.5, 0.5]
        gameView.playerDoesNotHaveCard(PlayerPosition.RIGHT, new Card(21));

        // Card 22: Partner (idx 1) doesn't have it. Prob -> [0.5, 0, 0.5]
        gameView.playerDoesNotHaveCard(PlayerPosition.ACROSS, new Card(22));
        // Card 23: Partner (idx 1) doesn't have it. Prob -> [0.5, 0, 0.5]
        gameView.playerDoesNotHaveCard(PlayerPosition.ACROSS, new Card(23));

        // Card 24: OppL (idx 2) doesn't have it. Prob -> [0.5, 0.5, 0]
        gameView.playerDoesNotHaveCard(PlayerPosition.LEFT, new Card(24));
        // Card 25: OppL (idx 2) doesn't have it. Prob -> [0.5, 0.5, 0]
        gameView.playerDoesNotHaveCard(PlayerPosition.LEFT, new Card(25));

        // Act
        int[] tokens = toIntArray(gameView.getTransformersTokensForCardChoice());

        // Assert
        assertEquals(Tokens.SECTION_BELIEF, tokens[68]);

        // Pos 1 (OppR, idx 0): Should have 50% prob for 22, 23, 24, 25
        assertEquals(Tokens.P_OPP_R, tokens[69]);
        // The order depends on getKMostProbableCards sorting, which is by prob (all 0.5)
        // and then implicitly by card number (map iteration order).
        // We'll just check that the 4 cards and their confidences are present.
        List<Integer> oppRCards = List.of(tokens[70], tokens[72], tokens[74], tokens[76]);
        List<Integer> oppRConfs = List.of(tokens[71], tokens[73], tokens[75], tokens[77]);

        List<Integer> expectedCardsR = List.of(Tokens.cardToken(22), Tokens.cardToken(23), Tokens.cardToken(24), Tokens.cardToken(25));
        assertEquals(4, oppRCards.stream().filter(expectedCardsR::contains).count());
        for(int conf : oppRConfs) assertEquals(Tokens.confidenceToken(0.5), conf);

        // Pos 2 (Partner, idx 1): Should have 50% prob for 20, 21, 24, 25
        assertEquals(Tokens.P_PARTNER, tokens[78]);
        List<Integer> partnerCards = List.of(tokens[79], tokens[81], tokens[83], tokens[85]);
        List<Integer> partnerConfs = List.of(tokens[80], tokens[82], tokens[84], tokens[86]);

        List<Integer> expectedCardsP = List.of(Tokens.cardToken(20), Tokens.cardToken(21), Tokens.cardToken(24), Tokens.cardToken(25));
        assertEquals(4, partnerCards.stream().filter(expectedCardsP::contains).count());
        for(int conf : partnerConfs) assertEquals(Tokens.confidenceToken(0.5), conf);

        // Pos 3 (OppL, idx 2): Should have 50% prob for 20, 21, 22, 23
        assertEquals(Tokens.P_OPP_L, tokens[87]);
        List<Integer> oppLCards = List.of(tokens[88], tokens[90], tokens[92], tokens[94]);
        List<Integer> oppLConfs = List.of(tokens[89], tokens[91], tokens[93], tokens[95]);

        List<Integer> expectedCardsL = List.of(Tokens.cardToken(20), Tokens.cardToken(21), Tokens.cardToken(22), Tokens.cardToken(23));
        assertEquals(4, oppLCards.stream().filter(expectedCardsL::contains).count());
        for(int conf : oppLConfs) assertEquals(Tokens.confidenceToken(0.5), conf);
    }
}

