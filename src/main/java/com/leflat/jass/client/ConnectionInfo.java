package com.leflat.jass.client;

public class ConnectionInfo {
    public String name;
    public String hostname;
    public int nbrArtificialPlayers;
    public int gameNumber;
    public boolean joinExistingGame = true;
    public boolean ok = true;
    public boolean local = false;

    public ConnectionInfo(boolean ok) {
        this.ok = ok;
    }

    public ConnectionInfo(String name) {
        this.name = name;
        this.local = true;
    }

    public ConnectionInfo(String name, String hostname, boolean joinExistingGame, int gameNumber, int nbrArtificialPlayers) {
        this.name = name;
        this.hostname = hostname;
        this.joinExistingGame = joinExistingGame;
        this.gameNumber = gameNumber;
        this.nbrArtificialPlayers = nbrArtificialPlayers;
    }
}
