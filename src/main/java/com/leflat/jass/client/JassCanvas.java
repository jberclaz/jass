package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class JassCanvas extends Canvas {
    public static final int MODE_STATIC = 0;
    public static final int MODE_PLAY = 1;

    protected int mode;   // 0 : rien, 1 : jouer
    protected String name = "";
    protected boolean atout = false;
    protected List<Card> hand = new ArrayList<>();

    protected JassCanvas() {
        mode = MODE_STATIC;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setAtout(boolean atout) {
        this.atout = atout;
        repaint();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHand(List<Card> hand) {
        clearHand();
        this.hand.addAll(hand);
        repaint();
    }

    public void clearHand() {
        hand.clear();
    }

    public void setBackCards(int number) {
        clearHand();
        for (int i=0; i<number; i++) {
            hand.add(Card.getBack());
        }
        repaint();
    }

    public void removeCard(Card card) {
        hand.remove(card);
        repaint();
    }

    public void removeCard(int position) {
        hand.remove(position);
        repaint();
    }

    public void removeCard() {
        removeCard(0);
        repaint();
    }
}
