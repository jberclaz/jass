package com.leflat.jass.common;

import com.leflat.jass.server.PlayerLeftExpection;

import java.util.List;

public interface INetwork {
    int sendMessage(String message);

    void setPlayerId(int id);

    int sendMessage(List<String> message);

    String[] receiveMessage() throws PlayerLeftExpection;
}
