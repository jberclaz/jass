package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePlayer {
    // Variables
    protected String name;
    protected int id;
    protected int team;
    protected List<Anouncement> anounces = new ArrayList<>(); // annonces

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

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public void addAnouncement(int type, Card card) {
        anounces.add(new Anouncement(type, card));
    }

    public List<Anouncement> getAnouncements() {
        return anounces;
    }

    public void clearAnouncement() {
        anounces.clear();
    }

    public String toString() { return name; }
}
