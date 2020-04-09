package com.leflat.jass.client;

import com.leflat.jass.common.IController;
import com.leflat.jass.common.IControllerFactory;
import com.leflat.jass.common.IPlayer;

public class RemoteControllerFactory implements IControllerFactory {
    @Override
    public IController getController(IPlayer player) {
        return new RemoteController(player);
    }
}
