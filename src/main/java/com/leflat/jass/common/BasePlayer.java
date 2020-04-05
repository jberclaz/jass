package com.leflat.jass.common;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePlayer {
    // Variables
    protected String name;
    protected int id;
    protected Team team;
    protected List<Anouncement> anoucements = new ArrayList<>(); // annonces

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

    public String toString() { return name; }
}
