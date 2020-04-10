package com.leflat.jass.common;

import com.leflat.jass.server.PlayerLeftExpection;

public interface IServerNetwork {
    void sendMessage(String... message);

    void setPlayerId(int id);

    String receiveRawMessage() throws PlayerLeftExpection;

    String[] receiveMessage() throws PlayerLeftExpection;
}
