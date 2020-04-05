package com.leflat.jass.common;

public abstract class ConnectionError {
    public static final int CONNECTION_SUCCESSFUL = 0;
    public static final int UNKNOWN_GAME = -1;
    public static final int GAME_FULL = -2;
    public static final int SERVER_UNREACHABLE = -3;
}
