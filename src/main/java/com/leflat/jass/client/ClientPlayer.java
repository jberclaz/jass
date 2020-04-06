package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;

public class ClientPlayer extends BasePlayer {
    public ClientPlayer(int id, String name) {
        super(id);
        this.name = name;
    }

    public ClientPlayer(int id) {
        super(id);
    }
}
