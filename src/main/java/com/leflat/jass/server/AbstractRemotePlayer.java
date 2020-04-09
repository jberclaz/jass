package com.leflat.jass.server;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.IPlayer;

public abstract class AbstractRemotePlayer extends BasePlayer implements IPlayer {
    public AbstractRemotePlayer(int id) {
        super(id);
    }
}
