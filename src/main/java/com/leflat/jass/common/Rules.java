package com.leflat.jass.common;

import java.util.List;

public class Rules {
    public static final int RULES_MUST_FOLLOW = 1;
    public static final int RULES_CANNOT_UNDERCUT = 2;


    public static boolean hasColor(List<Card> hand, int color) {
        return hand.stream().anyMatch(c -> c.getColor() == color);
    }

    public static boolean hasBourgSec(List<Card> hand) {
        int numberAtouts = 0;
        boolean bourg = false;
        for (var card : hand) {
            if (card.getColor() == Card.atout) {
                numberAtouts += 1;
                if (card.getRank() == Card.RANK_BOURG) {
                    bourg = true;
                }
            }
        }
        return bourg && numberAtouts == 1;
    }
}
