package com.leflat.jass.server;

public interface IServerNetwork {
    void sendStr(String strToSend);
    String rcvStr() throws ClientLeftException;
}
