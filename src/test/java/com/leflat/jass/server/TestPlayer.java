package com.leflat.jass.server;

import com.leflat.jass.common.BasePlayer;

public class TestPlayer extends BasePlayer {
    public TestPlayer(int id) {
        super(id);
    }

    public TestPlayer(int id, String name) {
        this(id);
        this.name = name;
    }
}
