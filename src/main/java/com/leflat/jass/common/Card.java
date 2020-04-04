package com.leflat.jass.common;

/**
 * ************************** REMARQUE ******************************
 * Les cartes sont en fait représentées par des int de 0 à 35
 * où "carte div 9" donne sa couleur et "carte mod 9" sa
 * hauteur. La classe Card est donc utilisée ici que pour
 * les méthodes statiques getColor et getHeight.
 * ***************************************************************
 */

public class Card {
    public static final int DIAMOND_SEVEN = 19;
    public static final int RANK_6 = 0;
    public static final int RANK_7 = 1;
    public static final int RANK_8 = 2;
    public static final int RANK_NELL = 3;
    public static final int RANK_10 = 4;
    public static final int RANK_BOURG = 5;
    public static final int RANK_DAME = 6;
    public static final int RANK_ROI = 7;
    public static final int RANK_AS = 8;

    public static final int COLOR_SPADE = 0;
    public static final int COLOR_HEART = 1;
    public static final int COLOR_DIAMOND = 2;
    public static final int COLOR_CLUB = 3;

    public static final String[] RANK_NAMES = {"six", "sept", "huit", "nell", "dix", "bourg", "dame", "roi", "as"};
    public static final String[] COLOR_NAMES = {"pique", "coeur", "carreau", "trefle"};

    public static final int[] VALUES = {0, 0, 0, 0, 10, 2, 3, 4, 11};
    public static final int[] VALUES_ATOUT = {0, 0, 0, 14, 10, 20, 3, 4, 11};

    public static final int BACK_NUMBER = 200;
    private static final Card backCard = new Card(BACK_NUMBER);

    private int number;

    public Card(int cardNumber) {
        number = cardNumber;
    }

    public Card(int rank, int color) {
        number = color * 9 + rank;
    }

    public int getColor() {
        return number / 9;
    }

    public int getRank() {
        return number % 9;
    }

    public int getNumber() {
        return number;
    }

    public String toString() {
        return RANK_NAMES[getRank()] + " de " + COLOR_NAMES[getColor()];
    }

    public int getValue() {
        return getValue(-1);
    }

    public int getValue(int atout) {
        return atout == getColor() ? VALUES_ATOUT[getRank()] : VALUES[getRank()];
    }

    public boolean isBack() { return number == BACK_NUMBER; }

    public static Card getBack() { return backCard; }
}
