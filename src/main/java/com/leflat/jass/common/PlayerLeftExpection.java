package com.leflat.jass.common;

public class PlayerLeftExpection extends Exception {
    int playerId;

    public PlayerLeftExpection(int clientID) {
        this.playerId = clientID;
    }

    public int getPlayerId() {
        return playerId;
    }
}
