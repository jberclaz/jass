package com.leflat.jass.common;


public class Plie {
    public int highest;   // la plus haute carte de la plie (celle qui tient la plie)
    public int color;     // la couleur demandée
    public int score;     // la valeur de la plie
    public boolean cut;     // 0 : pas coupé, 1 : coupé
    public int owner;     // iD de celui qui tient la plie

    public Plie(int color, int highestRank, boolean cut) {
        this.color = color;
        this.highest = highestRank;
        this.cut = cut;
    }
}
