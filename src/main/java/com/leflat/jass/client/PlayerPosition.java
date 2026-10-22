package com.leflat.jass.client;

public enum PlayerPosition {
    MYSELF, LEFT, RIGHT, ACROSS, NONE;

    public PlayerPosition partner() {
        return switch (this) {
            case MYSELF -> ACROSS;
            case ACROSS -> MYSELF;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case NONE -> NONE;
        };
    }
}
