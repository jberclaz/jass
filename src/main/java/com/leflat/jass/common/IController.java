package com.leflat.jass.common;

import com.leflat.jass.client.ServerDisconnectedException;

import java.util.List;
import java.util.concurrent.locks.Lock;

public interface IController extends Runnable {
    ClientConnectionInfo connect(String host, int requestGameId, String name);

    boolean isConnected();

    void sendRawMessage(String message);

    void sendMessage(List<String> message);

    String receiveRawMessage() throws ServerDisconnectedException;

    void disconnect();

    Lock getLock();
}
