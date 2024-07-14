package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.ArtificialPlayer;

import java.io.IOException;
import java.util.logging.*;

public class ArtificialClient extends ArtificialPlayer implements IRemotePlayer {
    protected final static Logger LOGGER = Logger.getLogger(ArtificialClient.class.getName());
    private final IClientNetworkFactory networkFactory;
    private IClientNetwork network = null;
    private IController controller = null;
    private Thread controllerThread = null;

    public ArtificialClient(String name, String host, int gameId,  IClientNetworkFactory networkFactory) {
        super(-1, name);
        this.networkFactory = networkFactory;
        int result = connect(name, host, gameId);
        if (result < 0) {
            LOGGER.warning("Unable to connect to game " + gameId + " : " + result);
        }
        else {
            LOGGER.info("Connected to game " + gameId);
            try {
                controllerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int connect(String name, String host, int gameId) {
        network = networkFactory.getClientNetwork();
        var connectionInfo = network.connect(host, gameId, name);
         if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
            network = null;
            return connectionInfo.error;
        }
        id = connectionInfo.playerId;
        positionsByIds.put(id, 0);
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

    public static void main(String[] args) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);
        try {
            for (var handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            var handler = new FileHandler("jass_ai-client-%u.%g.log");
            handler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String name = "FLAT";
        if (args.length < 2) {
            System.err.println("Missing parameters.");
            System.err.println("Usage: ArtificialClient <server_host> <game_id> [<name>]");
            return;
        }
        var serverHost = args[0];
        int gameId = Integer.parseInt(args[1]);
        if (args.length > 2) {
            name = args[2];
        }
        new ArtificialClient(name, serverHost, gameId, new ClientNetworkFactory());
    }
}
