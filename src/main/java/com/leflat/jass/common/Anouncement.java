package com.leflat.jass.common;

public class Anouncement {
    public static final int STOECK = 0;
    public static final int THREE_CARDS = 1;
    public static final int FIFTY = 2;
    public static final int HUNDRED = 3;
    public static final int SQUARE = 4;
    public static final int NELL_SQUARE = 5;
    public static final int BOURG_SQUARE = 6;

    public static final int[] VALUES = {20, 20, 50, 100, 100, 150, 200};
    public static final String[] NAMES = {"stoeck", "3 cartes", "cinquante", "cent", "cent", "cent cinquante", "deux cents"};

    public Anouncement(int type, Card card) {
        this.type = type;
        this.card = card;
    }

    private int type;     // 0: stöck, 1: 3 cartes, 2: cinquante, 3: cent, 4: carré
    private Card card;    // plus haute carte de l'annonce

    public int getType() {
        return type;
    }

    public Card getCard() {
        return card;
    }

    public int getValue() {
        if (type == SQUARE) {
            switch (card.getRank()) {
                case Card.RANK_BOURG:
                    return 200;
                case Card.RANK_NELL:
                    return 150;
                default:
                    return 100;
            }
        }
        return VALUES[type];
    }
}
