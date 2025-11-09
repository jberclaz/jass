package com.leflat.jass.common;

import java.util.HashMap;
import java.util.Map;

public enum PlayerPosition {
    SELF (0), RIGHT(1), ACROSS(2), LEFT(3), NONE(-1);

    private final int code;
    private static final int numPositions = 4;


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

    public static boolean ourTeam(PlayerPosition pos) {
        return pos.code % 2 == 0;
    }

    public boolean ourTeam() {
        return code % 2 == 0;
    }

    public PlayerPosition add(int steps) {
        // Cannot add steps to an invalid position
        if (this == NONE) {
            return NONE;
        }

        // This formula handles positive and negative 'steps' correctly,
        // wrapping around as needed.
        int newCode = (this.code + steps + numPositions) % numPositions;

        return PlayerPosition.fromCode(newCode);
    }

    /**
     * Returns the position 'steps' counter-clockwise away.
     *
     * @param steps The number of steps to rotate counter-clockwise.
     * @return The new PlayerPosition.
     */
    public PlayerPosition subtract(int steps) {
        return add(-steps);
    }

    /**
     * Gets the opposite position.
     * Uses the new add() logic.
     */
    public PlayerPosition opposite() {
        return this.add(2); // Opposite is always 2 steps away
    }

    /**
     * Static version of opposite().
     */
    public static PlayerPosition opposite(PlayerPosition pos) {
        return pos.add(2);
    }
}
