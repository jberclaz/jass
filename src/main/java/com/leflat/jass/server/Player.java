package com.leflat.jass.server;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;

import java.util.ArrayList;
import java.util.List;

public class Player {
    // Variables
    private String firstName;
    private String lastName;
    private int id;
    private int team;
    private List<Anouncement> anounces = new ArrayList<>(); // annonces
    private ServerNetwork connection;

    // Constructeur
    public Player(String firstName, String lastName, int id, ServerNetwork connection) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.connection = connection;
    }

    protected void finalize() {
        if (connection != null) {
            connection.close();
        }
    }

    // Méthodes
    public void clearAnounces() {
        anounces.clear();
    }

    public void addAnounce(int type, Card card) {
        anounces.add(new Anouncement(type, card));
    }

    public void sendMessage(String message) {
        connection.sendStr(message);
    }

    public String waitForAnswer() throws ClientLeftException {
        return connection.rcvStr();
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
}
