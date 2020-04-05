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
}
