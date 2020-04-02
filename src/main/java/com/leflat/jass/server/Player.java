package com.leflat.jass.server;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;

import java.util.ArrayList;
import java.util.List;

public class Player extends ServerRpc {
    // Variables
    private String firstName;
    private String lastName;
    private int id;
    private int team;
    private List<Anouncement> anounces = new ArrayList<>(); // annonces

    // Constructeur
    public Player(int id, IServerNetwork connection) throws ClientLeftException {
        super(connection);
        this.id = id;
        var answer = connectionAccepted(id);
        firstName = answer[0];
        lastName = answer[1];
    }

    // Méthodes
    public void clearAnounces() {
        anounces.clear();
    }

    public void addAnounce(int type, Card card) {
        anounces.add(new Anouncement(type, card));
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public int getNbrAnounces() {
        return anounces.size();
    }

    public Anouncement getAnouncement(int i) {
        return anounces.get(i);
    }

    public List<Anouncement> getAllAnouncements() { return anounces; }
}
