package com.leflat.jass.common;

import com.leflat.jass.client.ServerDisconnectedException;

import java.util.List;

public interface IClientNetwork {
    ClientConnectionInfo connect(String host, int requestedGameId, String name);

    boolean isConnected();

    void disconnect();

    void sendMessage(List<String> message);

    String receiveRawMessage() throws ServerDisconnectedException;
}
