package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(NAMES[type]);
        if (type == STOECK) {
            return sb.toString();
        }
        sb.append(" au ");
        sb.append(card.toString());
        return sb.toString();
    }

    public static List<Anouncement> findAnouncements(List<Card> hand) {
        var announcements = findSquares(hand);
        announcements.addAll(findSuits(hand));
        return new ArrayList<>(announcements);
    }

    public static boolean findStoeck(List<Card> hand, int atout) {
        int queen = atout * 9 + Card.RANK_DAME;
        int king = atout * 9 + Card.RANK_ROI;
        var numbers = hand.stream().map(Card::getNumber).collect(Collectors.toList());
        return numbers.contains(queen) && numbers.contains(king);
    }

    private static Collection<Anouncement> findSquares(List<Card> hand) {
        var announcements = new ArrayList<Anouncement>();
        int i = 0;
        while ((i < hand.size()) && ((hand.get(i).getColor()) == Card.COLOR_SPADE)) {   // tant que c'est du pique (couleur la + à gauche)
            int nbrCards = 1;
            var firstCard = hand.get(i);
            for (int j = i + 1; j < hand.size(); j++) {
                if ((hand.get(j).getRank()) == (firstCard.getRank())) {
                    nbrCards++;
                }
            }
            if ((nbrCards == 4) && ((firstCard.getRank()) > Card.RANK_8)) {       // carré trouvé
                if ((firstCard.getRank()) == Card.RANK_NELL)                      // cent-cinquante
                    announcements.add(new Anouncement(Anouncement.NELL_SQUARE, firstCard));
                else if ((firstCard.getRank()) == Card.RANK_BOURG)                // deux cents
                    announcements.add(new Anouncement(Anouncement.BOURG_SQUARE, firstCard));
                else                                                              // cent
                    announcements.add(new Anouncement(Anouncement.SQUARE, firstCard));
                System.out.println("Found suit: " + announcements.get(announcements.size()-1));
            }
            i++;
        }
        return announcements;
    }

    private static Collection<Anouncement> findSuits(List<Card> hand) {
        var announcements = new ArrayList<Anouncement>();
        for (int i = 0; i < hand.size() - 2; i++) {
            var firstCard = hand.get(i);
            int color = firstCard.getColor();
            int j = i + 1;
            int nbrCards = 1;
            while ((j < hand.size()) && (hand.get(j).getColor() == color)) {
                if (hand.get(j).getNumber() == (firstCard.getNumber() + j - i)) // si les cartes se suivent
                    nbrCards++;
                j++;
            }
            if (nbrCards > 2) {   // on a trouvé une suite
                if (nbrCards > 5) {
                    nbrCards = 5;
                }
                announcements.add(new Anouncement(nbrCards - 2, hand.get(i + nbrCards - 1)));
                System.out.println("Found suit: " + announcements.get(announcements.size()-1));
                i = j - 1;
            }
        }
        return announcements;
    }
}
