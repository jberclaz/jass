package com.leflat.jass.common;

import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePlayer {
    // Variables
    protected String name;
    protected int id;
    protected Team team;
    protected List<Announcement> announcements = new ArrayList<>(); // annonces
    protected List<Card> hand = new ArrayList<>();

    // Constructeur
    public BasePlayer(int id) {
        this.id = id;
    }

    // Méthodes
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) { this.name = name; }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public List<Announcement> getAnnouncements() throws PlayerLeftExpection {
        return announcements;
    }

    public void clearAnnouncements() {
        announcements.clear();
    }

    public List<Card> getHand() { return hand; }

    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        hand.clear();
        hand.addAll(cards);
    }

    public void removeCard(Card card) {
        hand.removeIf(c->c.getNumber() == card.getNumber());
    }

    public String toString() { return name; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BasePlayer)) {
            return false;
        }
        BasePlayer bp = (BasePlayer)obj;

        return bp.getId() == this.id;
    }
}
