package com.leflat.jass.common;

public class ClientConnectionInfo {
    public int playerId;
    public int gameId;
    public int error;

    public ClientConnectionInfo(int playerId, int gameId, int error) {
        this.playerId = playerId;
        this.gameId = gameId;
        this.error = error;
    }

    public ClientConnectionInfo(int error) {
        this.error = error;
        this.gameId = -1;
        this.playerId = -1;
    }
}
