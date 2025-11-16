package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.common.Plie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HeuristicStrategy implements IJassStrategy {
    @Override
    public Card chooseCard(List<Card> validCards, GameView gameView, Plie currentPlie, List<Card> hand, int pliesWonByTeam) {
        return chooseHeuristicMove(currentPlie, validCards);
    }

    @Override
    public int chooseTrumpSuit(boolean first, List<Card> hand, GameView gameView) {
        var counts = hand.stream().collect(Collectors.groupingBy(Card::getColor, Collectors.counting()));
        int bestSuit = -1;
        int highestCount = 0;
        for (var entry : counts.entrySet()) {
            long count = entry.getValue();
            if (count > highestCount) {
                bestSuit = entry.getKey();
                highestCount = (int)count;
            }
        }
        return bestSuit;
    }

    /**
     * Chooses a card to play based on a light, rule-based heuristic.
     *
     * @param plie        The current trick state.
     * @param validMoves  The list of legal cards to play.
     * @return The chosen card.
     */
    private Card chooseHeuristicMove(Plie plie, List<Card> validMoves) {
        // Sort moves from lowest power to highest power.
        // This makes finding the "cheapest" card easy.
        List<Card> sortedMoves = new ArrayList<>(validMoves);
        // **** MODIFICATION: Sort by power (getRankOrder) not by card number ****
        sortedMoves.sort(Comparator.comparingInt(Card::getRank));

        // Rule 2: Leading a new trick.
        if (plie.getSize() == 0) {
            // **** MODIFICATION: New "Lead" logic ****
            // Try to lead with the highest-power non-trump card.
            Card bestNonTrumpLead = null;
            for (Card card : sortedMoves) { // Iterate low-to-high power
                if (card.getColor() != Card.atout) {
                    bestNonTrumpLead = card; // This will keep the last, highest-power non-trump
                }
            }

            if (bestNonTrumpLead != null) {
                return bestNonTrumpLead; // Lead with highest-power non-trump
            }

            // If only trumps left, lead with lowest-power trump (safe play).
            return sortedMoves.getFirst();
        }

        // Rule 3: Following a trick.
        Card winningCard = plie.getWinningCard();
        boolean partnerIsWinning = plie.getSize() > 1 && ((plie.getSize() - plie.getWinningIndex()) % 2 == 0);

        if (partnerIsWinning) {
            // --- LOGIC: PARTNER IS WINNING ---
            // Goal: "Schmieren" (give points) if possible, but don't take the trick.
            // If not possible, lose as cheaply as possible.

            // Try to find the highest-scoring card that DOES NOT WIN.
            Card bestToSchmier = null;
            for (int i = sortedMoves.size() - 1; i >= 0; i--) { // Iterate high-to-low power
                Card card = sortedMoves.get(i);
                if (!isWinning(card, winningCard)) {
                    if (bestToSchmier == null || card.getValue() > bestToSchmier.getValue()) {
                        bestToSchmier = card;
                    }
                }
            }

            if (bestToSchmier != null) {
                return bestToSchmier; // Found a non-winning card, play the highest-scoring one.
            }

            // If all our valid moves win (e.g., must play trump over partner),
            // then play the lowest-power card to "steal" the trick as cheaply as possible.
            return sortedMoves.getFirst();

        } else {
            // --- LOGIC: OPPONENT IS WINNING (or we are) ---
            // Goal: Win as cheaply as possible. If not possible, lose as cheaply as possible.

            // 1. Find the cheapest (lowest-power) card that wins.
            for (Card card : sortedMoves) { // Iterate low-to-high power
                if (isWinning(card, winningCard)) {
                    return card; // Return the lowest-power card that wins.
                }
            }

            // 2. No card can win. Lose as cheaply as possible.
            // Play the lowest-scoring card.
            Card cheapestLoss = sortedMoves.getFirst();
            for (Card card : sortedMoves) {
                if (card.getValue() < cheapestLoss.getValue()) {
                    cheapestLoss = card;
                }
            }
            return cheapestLoss;
        }
    }

    /**
     * Helper to check if a new card beats an old card.
     */
    private boolean isWinning(Card newCard, Card oldCard) {
        // **** MODIFICATION: Handle first card played ****
        if (oldCard == null) {
            return true; // First card always "wins" initially
        }
        // New card is trump, old one is not
        if (newCard.getColor() == Card.atout && oldCard.getColor() != Card.atout) {
            return true;
        }
        // Old card is trump, new one is not
        if (newCard.getColor() != Card.atout && oldCard.getColor() == Card.atout) {
            return false;
        }
        // Both are same suit (either trump or non-trump)
        if (newCard.getColor() == oldCard.getColor()) {
            return newCard.compareTo(oldCard) > 0;
        }
        // Neither is trump, and new card does not follow suit (old card is lead suit)
        return false;
    }

}
