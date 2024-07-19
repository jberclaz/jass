package com.leflat.jass.common;

public interface IConnectable {
    int connect(String name, String host, int gameId);

    boolean disconnect();

    boolean isConnected();
}
