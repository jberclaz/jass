package com.leflat.jass.server;

import com.leflat.jass.common.Card;

import java.util.*;

public class GameView {
    private final Random rand = new Random();
    private final Map<Integer, Float[]> cardsInGame = new HashMap<>();
    private final Map<Integer, List<Card>> cardsInHands = new HashMap<>();

    public GameView() {
        for (int p = 0; p < 3; p++) {
            cardsInHands.put(p, new ArrayList<>());
        }
    }

    public void reset(List<Card> hand) {
        cardsInGame.clear();
        for (int p = 0; p < 3; p++) {
            cardsInHands.get(p).clear();
        }
        for (int i = 0; i < 36; i++) {
            var card = new Card(i);
            if (hand.contains((card))) {
                continue;
            }
            cardsInGame.put(i, new Float[]{0.3f, 0.3f, 0.3f});
        }
        assert getNumberCardsInGame() == 27;
    }

    public void cardPlayed(int player, Card card) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        var removed = cardsInGame.remove(card.getNumber());
        var removed2 = cardsInHands.get(player).remove(card);
        if (removed == null && !removed2) {
            System.out.println("warning!");
        }
        assert getNumberCardsInGame() == (previousNumberCardsInGame - 1);
    }

    public void playerHasCard(int player, int cardNumber) {
        int previousNumberCardsInGame = getNumberCardsInGame();
        cardsInGame.remove(cardNumber);
        cardsInHands.get(player).add(new Card(cardNumber));
        assert getNumberCardsInGame() == previousNumberCardsInGame;
    }

    public void playerHasCard(int player, Card card) {
        playerHasCard(player, card.getNumber());
    }

    public void playerDoesNotHaveCard(int player, int cardNumber) {
        int previousNumberCardsInGame = getNumberCardsInGame();
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
            if (!cardsInHands.get(p).isEmpty()) {
                s.append(p).append(": ");
                for (var card : cardsInHands.get(p)) {
                    s.append(card).append(" ");
                }
                s.append("\n");
            }
        }
        return s.toString();
    }

    public Map<Integer, List<Card>> getRandomHands(int p1num, int p2num, int p3num) {
        var hands = new HashMap<Integer, List<Card>>();
        for (int p = 0; p < 3; p++) {
            hands.put(p, new ArrayList<>());
        }
        for (var card : cardsInGame.entrySet()) {

        }
        return hands;
    }

    int getNumberCardsInGame() {
        return cardsInGame.size() + cardsInHands.get(0).size()+ cardsInHands.get(1).size()+ cardsInHands.get(2).size();
    }
}
