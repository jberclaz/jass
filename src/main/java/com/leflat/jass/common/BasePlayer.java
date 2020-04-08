package com.leflat.jass.common;

import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePlayer {
    // Variables
    protected String name;
    protected int id;
    protected Team team;
    protected List<Anouncement> anoucements = new ArrayList<>(); // annonces
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

    public void addAnouncement(Anouncement a) {
        anoucements.add(a);
    }

    public List<Anouncement> getAnouncements() {
        return anoucements;
    }

    public void clearAnouncement() {
        anoucements.clear();
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
}
