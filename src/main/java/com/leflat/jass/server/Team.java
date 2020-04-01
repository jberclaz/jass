package com.leflat.jass.server;

import java.util.ArrayList;
import java.util.List;

public class Team {
    // Variables
    private int currentScore;
    private List<Player> players = new ArrayList<>();

    // Constructeur
    public Team() {
        currentScore = 0;
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
        return currentScore > 1499;
    }

    public int getScore() {
        return currentScore;
    }

    public Player getPlayer(int i) {
        return players.get(i);
    }

    public void addPlayer(Player p) {
        players.add(p);
    }
}
