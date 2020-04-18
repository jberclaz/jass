package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * ************************** REMARQUE ******************************
 * Les cartes sont en fait représentées par des int de 0 à 35
 * où "carte div 9" donne sa couleur et "carte mod 9" sa
 * hauteur. La classe Card est donc utilisée ici que pour
 * les méthodes statiques getColor et getHeight.
 * ***************************************************************
 */

public class Card implements Comparable<Card> {
    public static final int DIAMOND_SEVEN = Card.COLOR_DIAMOND * 9 + 1;
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
    public static final int COLOR_CLUB = 2;
    public static final int COLOR_DIAMOND = 3;

    public static final String[] RANK_NAMES = {"six", "sept", "huit", "nell", "dix", "bourg", "dame", "roi", "as"};
    public static final String[] COLOR_NAMES = {"pique", "cœur", "trèfle", "carreau"};

    public static final int[] VALUES = {0, 0, 0, 0, 10, 2, 3, 4, 11};
    public static final int[] VALUES_ATOUT = {0, 0, 0, 14, 10, 20, 3, 4, 11};

    public static final int BACK_NUMBER = 200;
    private static final Card backCard = new Card(BACK_NUMBER);

    public static int atout;

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
        return getColor() == Card.atout ? VALUES_ATOUT[getRank()] : VALUES[getRank()];
    }

    public boolean isBack() {
        return number == BACK_NUMBER;
    }

    public static Card getBack() {
        return backCard;
    }

    public static void sort(List<Card> cards) {
        quickSortCards(cards, 0, cards.size() - 1);
    }

    private static void quickSortCards(List<Card> cards, int min, int max) {
        int i = min;
        int j = max;
        int x = cards.get((min + max) / 2).getNumber();
        do {
            while (cards.get(i).getNumber() < x)
                i++;
            while (x < cards.get(j).getNumber())
                j--;
            if (i <= j) {
                Collections.swap(cards, i, j);
                i++;
                j--;
            }
        } while (i <= j);
        if (min < j) {
            quickSortCards(cards, min, j);
        }
        if (i < max) {
            quickSortCards(cards, i, max);
        }
    }

    public static List<Card> shuffle(int number) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            cards.add(new Card(i));
        }
        Random rand = new Random();
        for (int i = number - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            if (i != j) {
                Collections.swap(cards, j, i);
            }
        }
        return cards;
    }

    @Override
    public int compareTo(Card card) {
        if (card.getColor() != this.getColor()) {
            throw new ClassCastException("Cannot compare cards of different colors");
        }
        if (this.getRank() == card.getRank()) {
            return 0;
        }
        if (card.getColor() == Card.atout) {
            if (card.getRank() == Card.RANK_BOURG) {
                return -1;
            }
            else if (this.getRank() == Card.RANK_BOURG) {
                return 1;
            }
            else if (card.getRank() == Card.RANK_NELL) {
                return -1;
            }
            else if (this.getRank() == Card.RANK_NELL) {
                return 1;
            }
        }
        if (this.getRank() < card.getRank()) {
            return -1;
        }
        return 1;
    }

     @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Card)) {
            return false;
        }

        Card c = (Card) o;

        return c.getNumber() == this.number;
    }
}
