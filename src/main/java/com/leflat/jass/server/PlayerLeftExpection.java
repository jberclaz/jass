package com.leflat.jass.server;

public class PlayerLeftExpection extends Exception {
    int playerId;

    public PlayerLeftExpection(int clientID) {
        this.playerId = clientID;
    }

    public int getPlayerId() {
        return playerId;
    }
}
