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

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof BasePlayer)) {
            return false;
        }

        BasePlayer p = (BasePlayer) o;

        if (p.getId() != this.id) {
            return false;
        }
        if (p.getName() == null || this.name == null) {
            return p.getName() == null && this.name == null;
        }
        return p.getName().compareTo(name) == 0;
    }
}
