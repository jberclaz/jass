package com.leflat.jass.common;


public class Plie {
    public int highest;   // la plus haute carte de la plie (celle qui tient la plie)
    public int color;     // la couleur demandée
    public int score;     // la valeur de la plie
    public boolean cut;     // 0 : pas coupé, 1 : coupé
    public BasePlayer owner = null;     // iD de celui qui tient la plie

    public Plie(Card card, int atout, BasePlayer owner) {
        color = card.getColor();
        highest = card.getRank();
        score = card.getValue(atout);
        this.owner = owner;
        cut = false;
    }

    public Plie(int color, int highestRank, boolean cut) {
        this.color = color;
        this.highest = highestRank;
        this.cut = cut;
    }

    public void playCard(Card card, int atout, BasePlayer player) throws BrokenRuleException {
        if (card.getColor() == atout) {
            if (color != atout) { // si on coupe
                if (cut) { // already cut
                    switch (card.getRank()) { // surcoupe
                        case Card.RANK_NELL:  // si on joue le nell
                            if (highest == Card.RANK_BOURG) {
                                throw new BrokenRuleException(Rules.RULES_CANNOT_UNDERCUT);
                            }
                            highest = card.getRank();
                            owner = player;
                            break;
                        case Card.RANK_BOURG:  // si on joue le bourg
                            highest = card.getRank();
                            owner = player;
                            break;
                        default: // sinon
                            if ((highest == Card.RANK_BOURG) ||
                                    (highest == Card.RANK_NELL) ||
                                    (card.getRank() < highest)) {
                                throw new BrokenRuleException(Rules.RULES_CANNOT_UNDERCUT);
                            }
                            highest = card.getRank();
                            owner = player;
                            break;
                    }
                    // else souscoupe => nothing to do
                } else {  // first to cut
                    cut = true;
                    highest = card.getRank();
                    owner = player;
                }
            } else {        // si c'est joué atout
                switch (card.getRank()) {
                    case Card.RANK_NELL: // si on joue le nell
                        if (highest != Card.RANK_BOURG) {
                            highest = card.getRank();
                            owner = player;
                        }
                        break;
                    case Card.RANK_BOURG:  // si on joue le bourg
                        highest = card.getRank();
                        owner = player;
                        break;
                    default: // sinon
                        if ((highest != Card.RANK_BOURG) &&
                                (highest != Card.RANK_NELL) &&
                                (card.getRank() > highest)) {
                            highest = card.getRank();
                            owner = player;
                        }
                        break;
                }
            }
        } else if (card.getColor() == color) {
            if ((card.getRank() > highest) && !cut) {
                highest = card.getRank();
                owner = player;
            }
        }
        score += card.getValue(atout); // augmente le score de la plie
    }
}
