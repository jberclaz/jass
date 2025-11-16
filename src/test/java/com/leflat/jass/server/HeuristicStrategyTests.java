package com.leflat.jass.server;

import com.leflat.jass.common.*;
        import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
        import static org.mockito.Mockito.*;

/**
 * Unit tests for the HeuristicStrategy class.
 * This class tests the deterministic logic of the heuristic.
 */
@ExtendWith(MockitoExtension.class)
public class HeuristicStrategyTests {

    private HeuristicStrategy strategy;

    // Mocks for Plie context
    @Mock private BasePlayer mockSelf, mockPartner, mockOpp1;
    private Team teamA, teamB;

    // Test cards
    // Spades (Trump)
    private final Card SPADE_6 = new Card(Card.RANK_6, Card.COLOR_SPADE);
    private final Card SPADE_7 = new Card(Card.RANK_7, Card.COLOR_SPADE);
    private final Card SPADE_8 = new Card(Card.RANK_8, Card.COLOR_SPADE);
    private final Card SPADE_10 = new Card(Card.RANK_10, Card.COLOR_SPADE);
    private final Card SPADE_BOURG = new Card(Card.RANK_BOURG, Card.COLOR_SPADE);
    private final Card SPADE_AS = new Card(Card.RANK_AS, Card.COLOR_SPADE);

    // Hearts (Non-Trump)
    private final Card HEART_6 = new Card(Card.RANK_6, Card.COLOR_HEART);
    private final Card HEART_7 = new Card(Card.RANK_7, Card.COLOR_HEART);
    private final Card HEART_10 = new Card(Card.RANK_10, Card.COLOR_HEART);
    private final Card HEART_ROI = new Card(Card.RANK_ROI, Card.COLOR_HEART);
    private final Card HEART_AS = new Card(Card.RANK_AS, Card.COLOR_HEART);

    // Diamonds (Non-Trump)
    private final Card DIAMOND_10 = new Card(Card.RANK_10, Card.COLOR_DIAMOND);


    @BeforeEach
    void setUp() {
        strategy = new HeuristicStrategy();

        // Set a default trump for all tests
        Card.atout = Card.COLOR_SPADE;

        // Setup mock teams
        teamA = new Team(0);
        teamB = new Team(1);
//        when(mockSelf.getTeam()).thenReturn(teamA);
//        when(mockPartner.getTeam()).thenReturn(teamA);
//        when(mockOpp1.getTeam()).thenReturn(teamB);
//
//        // Setup mock players
//        when(mockSelf.getId()).thenReturn(0);
//        when(mockPartner.getId()).thenReturn(2);
//        when(mockOpp1.getId()).thenReturn(1);
    }

    // --- chooseTrumpSuit Tests ---

    @Test
    void testChooseTrump_ReturnsMostNumerousSuit() {
        List<Card> hand = List.of(HEART_6, HEART_7, HEART_10, SPADE_6, SPADE_7, DIAMOND_10);
        // 3 Hearts, 2 Spades, 1 Diamond
        int suit = strategy.chooseTrumpSuit(true, hand, null);
        assertEquals(Card.COLOR_HEART, suit);
    }

    @Test
    void testChooseTrump_ReturnsLastMostNumerousSuitOnTie() {
        List<Card> hand = List.of(HEART_6, HEART_7, HEART_10, SPADE_6, SPADE_7, SPADE_8);
        // 3 Hearts, 3 Spades. Spades will be the last one found by the stream grouping
        int suit = strategy.chooseTrumpSuit(true, hand, null);
        assertEquals(Card.COLOR_SPADE, suit);
    }

    // --- chooseCard Tests ---

    @Test
    void testChooseCard_Lead_PlaysHighestNonTrump() {
        Plie plie = new Plie(); // Empty plie
        List<Card> validCards = List.of(HEART_7, HEART_ROI, SPADE_6); // Spades are trump

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Should play the highest-power non-trump card
        assertEquals(HEART_ROI, choice);
    }

    @Test
    void testChooseCard_Lead_PlaysLowestTrumpIfOnlyTrumps() {
        Plie plie = new Plie(); // Empty plie
        List<Card> validCards = List.of(SPADE_AS, SPADE_7, SPADE_BOURG); // Only trumps

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Should play the lowest-power trump card
        assertEquals(SPADE_7, choice);
    }

    @Test
    void testChooseCard_PartnerWinning_SchmierenHighestValueCard() throws BrokenRuleException {
        // Partner (P2) leads with high trump, Opponent (P1) plays low trump
        Plie plie = new Plie(SPADE_AS, mockPartner); // P2 leads Spade Ace
        plie.playCard(SPADE_6, mockOpp1, null);      // P1 plays Spade 6

        // Plie state: [Spade Ace, Spade 6], winning card = Spade Ace (index 0)
        // Size = 2, WinningIndex = 0. (2 - 0) % 2 == 0. Partner is winning.

        // Our valid cards:
        // Spade 7 (wins, 0 value)
        // Heart 10 (loses, 10 value)
        // Heart 6 (loses, 0 value)
        List<Card> validCards = List.of(SPADE_7, HEART_10, HEART_6);

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Should "schmier" the highest-value card that DOES NOT WIN
        assertEquals(HEART_10, choice);
    }

    @Test
    void testChooseCard_PartnerWinning_MustWinPlaysCheapestPower() throws BrokenRuleException {
        // Partner (P2) leads with high trump, Opponent (P1) plays low trump
        Plie plie = new Plie(SPADE_AS, mockPartner); // P2 leads Spade Ace
        plie.playCard(SPADE_6, mockOpp1, null);      // P1 plays Spade 6

        // Partner is winning.
        // Our valid cards are all trumps that will win:
        List<Card> validCards = List.of(SPADE_8, SPADE_BOURG);

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // All cards win. Must play the one with the lowest *power* (rank)
        assertEquals(SPADE_8, choice);
    }

    @Test
    void testChooseCard_OpponentWinning_WinAsCheaplyAsPossible_WithTrump() throws BrokenRuleException {
        // Opponent (P1) leads with high non-trump
        Plie plie = new Plie(HEART_AS, mockOpp1); // P1 leads Heart Ace

        // Plie state: [Heart Ace], winning card = Heart Ace (index 0)
        // Size = 1. Opponent is winning.

        // Our valid cards:
        // Heart 7 (loses)
        // Spade 6 (wins, low-power trump)
        // Spade Ace (wins, high-power trump)
        List<Card> validCards = List.of(HEART_7, SPADE_6, SPADE_AS);

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Should win with the *cheapest power* card
        assertEquals(SPADE_6, choice);
    }

    @Test
    void testChooseCard_OpponentWinning_WinAsCheaplyAsPossible_FollowSuit() throws BrokenRuleException {
        // Opponent (P1) leads with non-trump
        Plie plie = new Plie(HEART_ROI, mockOpp1); // P1 leads Heart King

        // Opponent is winning.

        // Our valid cards:
        // Heart 7 (loses)
        // Heart AS (wins)
        List<Card> validCards = List.of(HEART_7, HEART_AS);

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Should win with the *cheapest power* card that wins
        assertEquals(HEART_AS, choice);
    }

    @Test
    void testChooseCard_OpponentWinning_CannotWinPlayLowestValue() throws BrokenRuleException {
        // Opponent (P1) leads with high trump
        Plie plie = new Plie(SPADE_AS, mockOpp1); // P1 leads Spade Ace

        // Opponent is winning.

        // Our valid cards (all lose):
        // Spade 7 (loses, 0 value)
        // Heart 10 (loses, 10 value)
        // Diamond 10 (loses, 10 value)
        // Heart 6 (loses, 0 value)
        List<Card> validCards = List.of(SPADE_7, HEART_10, DIAMOND_10, HEART_6);

        Card choice = strategy.chooseCard(validCards, null, plie, null, 0);

        // Cannot win. Must play the card with the lowest *value*.
        // Both Spade 7 and Heart 6 have 0 value. The heuristic will pick
        // the one with the lowest power (rank), which is Heart 6 (rank 0).
        // If Spade 7 (rank 1) and Heart 6 (rank 0) are options, it will find
        // Heart 6 as the cheapest loss by value, and it's sorted by power,
        // so it will iterate and find the lowest value card.

        // Let's trace the logic:
        // 1. Find cheapest winning card: None found.
        // 2. Find cheapest loss (by value).
        //    - cheapestLoss = sortedMoves.getFirst() [Spade 7 after sorting by rank]
        //    - Iterate:
        //    - card = HEART_10 (val 10) -> no change
        //    - card = DIAMOND_10 (val 10) -> no change
        //    - card = HEART_6 (val 0) -> cheapestLoss = HEART_6
        //    - card = SPADE_7 (val 0) -> no change
        // The test cards need to be sorted by rank first.
        // Valid cards: [SPADE_7, HEART_10, DIAMOND_10, HEART_6]
        // Sorted by rank: [HEART_6, SPADE_7, HEART_10, DIAMOND_10]
        //
        // 1. Find cheapest winning card:
        //    - isWinning(HEART_6, SPADE_AS) -> false
        //    - isWinning(SPADE_7, SPADE_AS) -> false
        //    - isWinning(HEART_10, SPADE_AS) -> false
        //    - isWinning(DIAMOND_10, SPADE_AS) -> false
        //    -> No winning card.
        //
        // 2. Find cheapest loss (by value):
        //    - cheapestLoss = sortedMoves.getFirst() = HEART_6 (value 0)
        //    - card = SPADE_7 (value 0). 0 < 0 is false. no change.
        //    - card = HEART_10 (value 10). 10 < 0 is false. no change.
        //    - card = DIAMOND_10 (value 10). 10 < 0 is false. no change.
        //    - Returns cheapestLoss = HEART_6

        assertEquals(HEART_6, choice);
    }
}