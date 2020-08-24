package com.leflat.jass.server;

import com.leflat.jass.common.Card;

import java.util.ArrayList;
import java.util.List;

public class PlayerView {
    private int id;
    private List<Card> knownHand = new ArrayList<>();
    private int handSize;

    public void reset() {
        knownHand.clear();
        handSize = 9;
    }

    public void removeCard(Card card) {
        handSize --;
        knownHand.remove(card);
    }

    public void addKnownCards(Card[] cards) {
        for (var card : cards) {
            if (!knownHand.contains(card)) {
                knownHand.add(card);
            }
        }
    }
}
