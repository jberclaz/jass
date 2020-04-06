package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.List;

public class Team {
    // Variables
    private int currentScore;
    private List<BasePlayer> players = new ArrayList<>();
    private int id;
    public static final int WINNING_SCORE = 1500;

    // Constructeur
    public Team(int id) {
        currentScore = 0;
        this.id = id;
    }

    // Méthodes
    public void resetScore() {
        currentScore = 0;
    }

    public void reset() {
        players.clear();
        resetScore();
    }

    public void addScore(int score) {
        currentScore += score;
    }

    public boolean hasWon() {
        return currentScore >= WINNING_SCORE;
    }

    public int getScore() {
        return currentScore;
    }

    public BasePlayer getPlayer(int i) {
        return players.get(i);
    }

    public void addPlayer(BasePlayer p) {
        players.add(p);
        p.setTeam(this);
    }

    public void addAnnoucementScore(List<Anouncement> anouncements, int atout) {
        for (var a : anouncements) {
            currentScore += atout == Card.COLOR_SPADE ? 2 * a.getValue() : a.getValue();
        }
    }

    public int getId() { return id; }
}
