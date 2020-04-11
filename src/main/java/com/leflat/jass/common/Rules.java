package com.leflat.jass.common;

import java.util.List;

public class Rules {
    public static final int RULES_OK = 0;
    public static final int RULES_MUST_FOLLOW = 1;
    public static final int RULES_CANNOT_UNDERCUT = 2;

/*
    public static int canPlay(Card card, Plie currentPlie, List<Card> hand, int atoutColor) {
        if (currentPlie.color < 0) {
            return RULES_OK;
        }
        if (card.getColor() == currentPlie.color) {
            return RULES_OK;
        }
        if (card.getColor() == atoutColor) {
            if (!currentPlie.cut) {
                return RULES_OK;
            }

            switch (card.getRank()) {
                case Card.RANK_BOURG:
                    return RULES_OK;
                case Card.RANK_NELL:
                    if (currentPlie.highest == Card.RANK_BOURG) {
                        return RULES_CANNOT_UNDERCUT;     // on ne peut pas sous-couper
                    }
                    return RULES_OK;       // nell sans bourg : ok!
                default:
                    if ((currentPlie.highest == Card.RANK_BOURG) ||
                            (currentPlie.highest == Card.RANK_NELL) ||
                            (currentPlie.highest > card.getRank())) {
                        return RULES_CANNOT_UNDERCUT;  // on ne peut pas sous-couper
                    }
                    return RULES_OK;
            }
        } else {    // carte jouée pas d'atout
            if (!hasColor(hand, currentPlie.color)) {
                return RULES_OK;
            }
            // The player owns cards from the required color
            if ((currentPlie.color == atoutColor) && hasBourSec(hand, atoutColor)) {
                return RULES_OK; // bourg sec -> ok
            }
            return RULES_MUST_FOLLOW;
        }
    }

 */

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
