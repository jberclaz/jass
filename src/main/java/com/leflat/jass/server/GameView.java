package com.leflat.jass.server;

import com.leflat.jass.common.Card;

import java.util.List;

public class GameView {
    private final Float[][] cardsInGame = new Float[3][36];

    public void reset(List<Card> hand) {
        for (int i = 0; i < 36; i++) {
            var card = new Card(i);
            float prob = hand.contains(card) ? 0f : 1 / 3f;
            for (int p = 0; p < 3; p++) {
                cardsInGame[p][i] = prob;
            }
        }
    }

    public void cardPlayed(Card card) {
        int number = card.getNumber();
        for (int p = 0; p < 3; p++) {
            cardsInGame[p][number] = 0f;
        }
    }

    public void playerHasCard(int player, int cardNumber) {
        for (int p = 0; p < 3; p++) {
            cardsInGame[p][cardNumber] = p == player ? 1f : 0f;
        }
    }

    public void playerHasCard(int player, Card card) {
        playerHasCard(player, card.getNumber());
    }

    public void playerDoesNotHaveCard(int player, int cardNumber) {
        if (cardsInGame[player][cardNumber] > 0) {
            cardsInGame[player][cardNumber] = 0f;
            normalize(cardNumber);
        }
    }

    public void playerDoesNotHaveCard(int player, Card card) {
        playerDoesNotHaveCard(player, card.getNumber());
    }

    private void normalize(int card) {
        float sum = cardsInGame[0][card] + cardsInGame[1][card] + cardsInGame[2][card];
        if (sum > 0) {
            for (int p = 0; p < 3; p++) {
                cardsInGame[p][card] /= sum;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (int card=0; card<36; card++) {
             float sum = cardsInGame[0][card] + cardsInGame[1][card] + cardsInGame[2][card];
             if (sum > 0) {
                 s.append(new Card(card).toString()).append(" : ").append(cardsInGame[0][card]).append(", ").append(cardsInGame[1][card]).append(", ").append(cardsInGame[2][card]).append("\n");
             }
        }
        return s.toString();
    }
}
