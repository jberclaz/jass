package com.leflat.jass.common;

import java.util.HashMap;
import java.util.Map;

public enum PlayerPosition {
    SELF (0), RIGHT(1), ACROSS(2), LEFT(3), NONE(-1);

    private final int code;


    PlayerPosition(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    // Optional reverse lookup
    private static final Map<Integer, PlayerPosition> lookup = new HashMap<>();
    static {
        for (PlayerPosition s : PlayerPosition.values()) {
            lookup.put(s.getCode(), s);
        }
    }

    public static PlayerPosition fromCode(int code) {
        return lookup.get(code);
    }

    public static PlayerPosition opposite(PlayerPosition pos) {
        return lookup.get((pos.code + 2) % 4);
    }

    public PlayerPosition opposite() { return lookup.get((this.code + 2) % 4);}

    public static boolean ourTeam(PlayerPosition pos) {
        return pos.code % 2 == 0;
    }

    public boolean ourTeam() {
        return code % 2 == 0;
    }
}
