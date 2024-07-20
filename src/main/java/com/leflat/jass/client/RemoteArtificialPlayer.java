package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.ArtificialPlayer;

import java.util.logging.Level;

public class RemoteArtificialPlayer extends ArtificialPlayer implements IConnectable {
    private final IClientNetworkFactory networkFactory;
    private IClientNetwork network = null;
    private IController controller = null;
    private Thread controllerThread = null;

    public RemoteArtificialPlayer(IClientNetworkFactory networkFactory) {
        super();
        this.networkFactory = networkFactory;
    }

    @Override
    public int connect(String name, String host, int gameId) {
        this.name = name;
        network = networkFactory.getClientNetwork();
        var connectionInfo = network.connect(host, gameId, name);
        if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
            network = null;
            return connectionInfo.error;
        }
        id = connectionInfo.playerId;
        controller = new RemoteController(this, network);
        controllerThread = new Thread(controller, "controller-thread");
        controllerThread.start();
        return connectionInfo.gameId;
    }

    @Override
    public boolean disconnect() {
        if (controller != null) {
            controller.terminate();
        }
        network.disconnect();
        if (controllerThread != null) {
            try {
                controllerThread.join(500);
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error while waiting for controller thread to die", e);
            }
        }
        controller = null;
        controllerThread = null;
        return true;
    }

    @Override
    public boolean isConnected() {
        return network != null && network.isConnected();
    }
}
