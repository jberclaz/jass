package com.leflat.jass.server;

import com.leflat.jass.common.Card;

import java.util.*;

public class GameView {
    private final Random rand = new Random();
    private final Map<Integer, Float[]> cardsInGame = new HashMap<>();
    private final List<Card>[] cardsInHands = new List[3];
    private final Map<Integer, Integer> handSizes = new HashMap<>();

    public GameView() {
        for (int p = 0; p < 3; p++) {
            cardsInHands[p] = new ArrayList<>();
            handSizes.put(p, 0);
        }
    }

    public void reset(List<Card> hand) {
        cardsInGame.clear();
        for (int p = 0; p < 3; p++) {
            cardsInHands[p].clear();
            handSizes.put(p, 9);
        }
        for (int i = 0; i < 36; i++) {
            var card = new Card(i);
            if (hand.contains((card))) {
                continue;
            }
            cardsInGame.put(i, new Float[]{1 / 3f, 1 / 3f, 1 / 3f});
        }
        assert getNumberCardsInGame() == 27;
    }

    public void cardPlayed(int player, Card card) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        var removed = cardsInGame.remove(card.getNumber());
        var removed2 = cardsInHands[player].remove(card);
        handSizes.put(player, handSizes.get(player) - 1);
        if (removed == null && !removed2) {
            System.out.println("warning!");
        }
        assert getNumberCardsInGame() == (previousNumberCardsInGame - 1);
    }

    public void playerHasCard(int player, int cardNumber) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (!cardsInGame.containsKey(cardNumber)) {
            return;
        }
        var card = new Card(cardNumber);
        if (cardsInHands[player].contains(card)) {
            throw new RuntimeException("We already know player " + player + " has card " + card);
        }
        var removedCard = cardsInGame.remove(cardNumber);
        if (removedCard == null) {
            throw new RuntimeException("Card " + new Card(cardNumber) + " was not in game");
        }
        cardsInHands[player].add(card);
        assert getNumberCardsInGame() == previousNumberCardsInGame : getNumberCardsInGame() + " != " + previousNumberCardsInGame;
    }

    public void playerHasCard(int player, Card card) {
        playerHasCard(player, card.getNumber());
    }

    public void playerDoesNotHaveCard(int player, int cardNumber) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (cardsInHands[player].contains(new Card(cardNumber))) {
            throw new RuntimeException("Contradictory game view");
        }
        var prob = cardsInGame.get(cardNumber);
        if (prob == null) {
            return;
        }
        if (prob[player] > 0) {
            prob[player] = 0f;
            normalize(cardNumber);
        }
        assert getNumberCardsInGame() == previousNumberCardsInGame;
    }

    public void playerDoesNotHaveCard(int player, Card card) {
        playerDoesNotHaveCard(player, card.getNumber());
    }

    private void normalize(int cardNumber) {
        var prob = cardsInGame.get(cardNumber);
        float sum = prob[0] + prob[1] + prob[2];
        if (sum == 0) {
            throw new RuntimeException("Sum should never be zero");
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
        for (var card : cardsInGame.entrySet()) {
            var prob = card.getValue();
            s.append(new Card(card.getKey()).toString()).append(" : ").append(prob[0]).append(", ").append(prob[1]).append(", ").append(prob[2]).append("\n");
        }
        for (int p = 0; p < 3; p++) {
            if (!cardsInHands[p].isEmpty()) {
                s.append(p).append(": ");
                for (var card : cardsInHands[p]) {
                    s.append(card).append(" ");
                }
                s.append("\n");
            }
        }
        return s.toString();
    }

    public Map<Integer, List<Card>> getRandomHands() {
        assert getNumberCardsInGame() == (handSizes.get(0) + handSizes.get(1) + handSizes.get(2));
        var hands = new HashMap<Integer, List<Card>>();
        for (int p = 0; p < 3; p++) {
            hands.put(p, new ArrayList<>(cardsInHands[p]));
        }
        int[] cardsSum = new int[3];
        for (var prob : cardsInGame.values()) {
            for (int p = 0; p < 3; p++) {
                if (prob[p] > 0) {
                    cardsSum[p]++;
                }
            }
        }

        var sortedEntrySet = new ArrayList<>(cardsInGame.entrySet());
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
                if (cardsSum[p] == handSizes.get(p) - hands.get(p).size()) {
                    if (probs[p] > 0) {
                        hands.get(p).add(new Card(card.getKey()));
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
            } while (hands.get(player).size() == handSizes.get(player));
            hands.get(player).add(new Card(card.getKey()));
        }
        return hands;
    }

    int getNumberCardsInGame() {
        return cardsInGame.size() + cardsInHands[0].size() + cardsInHands[1].size() + cardsInHands[2].size();
    }
}
