package com.leflat.jass.server;

import com.leflat.jass.common.Card;

import java.util.*;
import java.util.logging.Logger;

public class GameView {
    protected final static Logger LOGGER = Logger.getLogger(GameView.class.getName());
    private final Random rand = new Random();
    private final Map<Integer, Float[]> unknownCardsInGame = new HashMap<>();
    private final List<Card>[] knownCardsInHands = new List[3];
    private final int[] handSizes = new int[3];

    public GameView() {
        for (int p = 0; p < 3; p++) {
            knownCardsInHands[p] = new ArrayList<>();
        }
    }

    public void reset(List<Card> ownHand) {
        unknownCardsInGame.clear();
        for (int p = 0; p < 3; p++) {
            knownCardsInHands[p].clear();
            handSizes[p] = 9;
        }
        for (int i = 0; i < Card.DECK_SIZE; i++) {
            var card = new Card(i);
            if (ownHand.contains((card))) {
                continue;
            }
            unknownCardsInGame.put(i, new Float[]{1 / 3f, 1 / 3f, 1 / 3f});
        }
        assert getNumberCardsInGame() == 27;
    }

    public void cardPlayed(int player, Card card) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        var removedCardFromGame = unknownCardsInGame.remove(card.getNumber());
        var removedCardFromHand = knownCardsInHands[player].remove(card);
        if ((removedCardFromGame != null) == removedCardFromHand) {
            throw new RuntimeException("Card " + card + " played by player " + player + " was not properly accounted for in GameView");
        }
        handSizes[player] --;
        assert getNumberCardsInGame() == (previousNumberCardsInGame - 1);
    }

    public void playerHasCard(int player, int cardNumber) {
        playerHasCard(player, new Card(cardNumber));
    }

    public void playerHasCard(int player, Card card) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (!unknownCardsInGame.containsKey(card.getNumber())) {
            return;
        }
        if (knownCardsInHands[player].contains(card)) {
            throw new RuntimeException("We already know player " + player + " has card " + card);
        }
        unknownCardsInGame.remove(card.getNumber());
        knownCardsInHands[player].add(card);
        assert getNumberCardsInGame() == previousNumberCardsInGame : getNumberCardsInGame() + " != " + previousNumberCardsInGame;
    }

    public void playerDoesNotHaveCard(int player, int cardNumber) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (knownCardsInHands[player].contains(new Card(cardNumber))) {
            throw new RuntimeException("Contradictory game view: strategy believes that player " + player + " has card " + cardNumber);
        }
        var prob = unknownCardsInGame.get(cardNumber);
        if (prob == null) {
            return;
        }
        if (prob[player] > 0) {
            prob[player] = 0f;
            normalizeCardsProbabilities(cardNumber);
        }
        assert getNumberCardsInGame() == previousNumberCardsInGame;
    }

    public void playerDoesNotHaveCard(int player, Card card) {
        playerDoesNotHaveCard(player, card.getNumber());
    }

    private void normalizeCardsProbabilities(int cardNumber) {
        var prob = unknownCardsInGame.get(cardNumber);
        float sum = prob[0] + prob[1] + prob[2];
        if (sum == 0) {
            throw new RuntimeException("Probabilities should not sum to zero");
        }
        int unique = -1;
        for (int p = 0; p < 3; p++) {
            if (prob[p] == sum) {
                unique = p;
                break;
            }
            prob[p] /= sum;
        }
        if (unique >= 0) {
            playerHasCard(unique, cardNumber);
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (var card : unknownCardsInGame.entrySet()) {
            var prob = card.getValue();
            s.append(new Card(card.getKey()).toString()).append(" : ").append(prob[0]).append(", ").append(prob[1]).append(", ").append(prob[2]).append("\n");
        }
        for (int p = 0; p < 3; p++) {
            if (!knownCardsInHands[p].isEmpty()) {
                s.append(p).append(": ");
                for (var card : knownCardsInHands[p]) {
                    s.append(card).append(" ");
                }
                s.append("\n");
            }
        }
        return s.toString();
    }

    public List<Card>[] getRandomHands() {
        assert getNumberCardsInGame() == (handSizes[0] + handSizes[1] + handSizes[2]);
        List<Card>[] hands = new List[3];
        for (int p = 0; p < 3; p++) {
            hands[p] = new ArrayList<>(knownCardsInHands[p]);
        }
        int[] cardsSum = new int[3];
        for (var prob : unknownCardsInGame.values()) {
            for (int p = 0; p < 3; p++) {
                if (prob[p] > 0) {
                    cardsSum[p]++;
                }
            }
        }

        var sortedEntrySet = new ArrayList<>(unknownCardsInGame.entrySet());
        sortedEntrySet.sort((e1, e2) -> {
            var p1 = e1.getValue();
            var p2 = e2.getValue();
            int zeros1 = 0, zeros2 = 0;
            for (int p = 0; p < 3; p++) {
                if (p1[p] == 0) {
                    zeros1++;
                }
                if (p2[p] == 0) {
                    zeros2++;
                }
            }
            return zeros2 - zeros1;
        });
        for (var card : sortedEntrySet) {
            var probs = card.getValue();
            boolean attributed = false;
            for (int p = 0; p < 3; p++) {
                if (cardsSum[p] == handSizes[p] - hands[p].size()) {
                    if (probs[p] > 0) {
                        hands[p].add(new Card(card.getKey()));
                        attributed = true;
                        break;
                    }
                }
            }
            for (int p = 0; p < 3; p++) {
                if (probs[p] > 0) {
                    cardsSum[p]--;
                }
            }
            if (attributed) {
                continue;
            }
            int player = -1;
            do {
                float sumProb = 0;
                float f = rand.nextFloat();
                for (int p = 0; p < 3; p++) {
                    sumProb += probs[p];
                    if (f < sumProb) {
                        player = p;
                        break;
                    }
                }
            } while (hands[player].size() == handSizes[player]);
            hands[player].add(new Card(card.getKey()));
        }
        return hands;
    }

    int getNumberCardsInGame() {
        return unknownCardsInGame.size() + knownCardsInHands[0].size() + knownCardsInHands[1].size() + knownCardsInHands[2].size();
    }
}
