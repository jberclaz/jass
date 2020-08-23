package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.List;

public class Team {
    // Variables
    private int currentScore;
    private final List<BasePlayer> players = new ArrayList<>();
    private final int id;
    private int numberOfPlies;
    public static final int WINNING_SCORE = 1500;

    // Constructeur
    public Team(int id) {
        currentScore = 0;
        this.id = id;
        numberOfPlies = 0;
    }

    // Méthodes
    public void resetScore() {
        currentScore = 0;
    }

    public void reset() {
        players.clear();
        resetScore();
        numberOfPlies = 0;
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
        assert players.size() < 2;
        players.add(p);
        p.setTeam(this);
    }

    public void addAnnouncementScore(List<Announcement> anouncements) {
        for (var a : anouncements) {
            currentScore += Card.atout == Card.COLOR_SPADE ? 2 * a.getValue() : a.getValue();
        }
    }

    public int getId() {
        return id;
    }

    public void addPlie() {
        numberOfPlies++;
    }

    public void resetPlies() {
        numberOfPlies = 0;
    }

    public int getNumberOfPlies() {
        return numberOfPlies;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Team)) {
            return false;
        }

        Team t = (Team) obj;

        return t.id == this.id;
    }
}
