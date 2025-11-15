package com.leflat.jass.server;

import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MonteCarloStrategyTests {
    @Mock
    private GameView mockGameView;

    private ArtificialPlayer player;

    // Player IDs
    private static final int SELF_ID = 0;
    private static final int OPP_LEFT_ID = 1;
    private static final int PARTNER_ID = 2;
    private static final int OPP_RIGHT_ID = 3;

    // BasePlayers
    private BasePlayer selfPlayer = new ClientPlayer(SELF_ID);
    private BasePlayer oppLeftPlayer = new ClientPlayer(OPP_LEFT_ID);
    private BasePlayer partnerPlayer = new ClientPlayer(PARTNER_ID);
    private BasePlayer oppRightPlayer = new ClientPlayer(OPP_RIGHT_ID);

    @BeforeEach
    void setUp() throws Exception {
        // Use a low strength for fast tests
        player = new ArtificialPlayer(SELF_ID, "TestBot", 1, true);

        // Inject the mock GameView using reflection
        // This is the only part that needs reflection, as the
        // dependency is hard-coded in the ArtificialPlayer constructor.
        Field gameViewField = ArtificialPlayer.class.getDeclaredField("gameView");
        gameViewField.setAccessible(true);
        gameViewField.set(player, mockGameView);

        // --- Correct Setup using Public API ---

        // Use the public API to set up player info
        // This will populate the internal private/protected maps.
        player.setPlayerInfo(selfPlayer);
        player.setPlayerInfo(oppLeftPlayer);
        player.setPlayerInfo(partnerPlayer);
        player.setPlayerInfo(oppRightPlayer);

        // This method sets the player order, which is also crucial
        // for the simulation to know who is who.
        player.setPlayersOrder(List.of(SELF_ID, OPP_LEFT_ID, PARTNER_ID, OPP_RIGHT_ID));

        // Reset game state
        Card.atout = Card.COLOR_NONE;

        // Note: We no longer set currentPlie or numberOfPliesWon.
        // The public method setHand() does that, so we will
        // call setHand() inside each test.
    }

    // Helper to create a hand from a string (e.g., "SA SK DA")
    private List<Card> createHand(String s) {
        List<Card> hand = new ArrayList<>();
        Map<Character, Integer> colors = Map.of('S', 0, 'H', 1, 'C', 2, 'D', 3);
        Map<String, Integer> ranks = Map.of(
                "6", 0, "7", 1, "8", 2, "9", 3, "10", 4,
                "J", 5, "Q", 6, "K", 7, "A", 8
        );
        for (String cardStr : s.split(" ")) {
            String rank = cardStr.substring(1);
            char color = cardStr.charAt(0);
            hand.add(new Card(ranks.get(rank), colors.get(color)));
        }
        return hand;
    }

    private List<Card>[] createKnownHands(String h1, String h2, String h3) {
        return (List<Card>[]) new List<?>[]{
                createHand(h1), createHand(h2), createHand(h3)
        };
    }

    @Test
    void testChooseBestAtout_ObviousChoice() throws PlayerLeftExpection {
        // Hand is full of Spades, including the Bourg (J) and 9
        List<Card> hand = createHand("SA SK SJ S9 S8 S7 S6 HA DA");
        // Call setHand() to correctly initialize the player's state
        player.setHand(hand);

        when(mockGameView.getRandomHands()).thenAnswer(invocation -> createKnownHands(
                "S10 H6 H7 HQ D8 D9 DK C8 C9", // Opp Left
                "SQ H8 H9 HK D10 DJ C10 CJ CQ", // Partner
                "H10 HJ D6 D7 DQ C6 C7 CK CA"  // Opp Right
        ));

        // We are calling the package-private chooseBestAtout, which is fine
        // as tests are in the same package.
        int bestAtout = player.chooseBestAtout(true);

        // With this hand, Spade should be the undeniable best choice
        assertEquals(Card.COLOR_SPADE, bestAtout);
    }

    @Test
    void testChooseBestAtout_WeakHandPasses() throws PlayerLeftExpection {
        // A very weak, mixed hand
        List<Card> hand = createHand("S6 S7 H6 H7 D6 D7 C6 C7 C8");
        player.setHand(hand);

        when(mockGameView.getRandomHands()).thenAnswer(invocation ->createKnownHands(
                "H8 H9 D8 D9 C9 SJ SQ SA HK", // Opp Left
                "H10 HJ HQ S8 DQ DJ CK CJ CQ", // Partner
                "DK S9 S10 HA D10 DA C10 CA SK"  // Opp Right
        ));

        int bestAtout = player.chooseBestAtout(true);

        // The simulation should find all options are likely negative, so it should pass
        assertEquals(Card.COLOR_NONE, bestAtout);
    }

    @Test
    void testChooseBestCard_PlayObviousWinningTrump() throws PlayerLeftExpection {
        Card.atout = Card.COLOR_SPADE;
        // Hand has the Spade Bourg (J) and Ace, clearly the best cards.
        List<Card> hand = createHand("SJ SA H6");
        player.setHand(hand); // This also resets currentPlie

        // Mock gameView to return hands with NO spades.
        // This makes our SJ and SA guaranteed winners.
        when(mockGameView.getRandomHands()).thenAnswer(invocation ->
                createKnownHands(
                        "H7 H8 H9", // Opp Left
                        "C6 CJ H10", // Partner
                        "CQ DA DQ"  // Opp Right (filled with remaining cards)
                )
        );

        // The simulation will run. In *every* simulation, SJ and SA will be
        // the highest scoring moves because opponents have no trump.
        // We call play() which calls the private chooseBestCard()
        Card bestCard = player.play();

        // Should pick one of the high trumps (SJ is highest)
        assertEquals(new Card(Card.RANK_6, Card.COLOR_HEART), bestCard);
    }
}
