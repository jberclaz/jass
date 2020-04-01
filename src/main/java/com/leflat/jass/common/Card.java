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

    public static final String[] RANK_NAMES = {"six", "sept", "huit", "nell", "dix", "bourg", "dame", "roi", "as"};
    public static final String[] COLOR_NAMES = {"pique", "coeur", "carreau", "trefle"};

    private int number;

    public Card(int cardNumber) {
        number = cardNumber;
    }

    public Card(int color, int rank) {
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
}
