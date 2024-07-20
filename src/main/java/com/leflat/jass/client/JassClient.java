package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.io.IOException;
import java.util.logging.*;

public class JassClient {
    public static void main(String[] args) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);
        try {
            for (var handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            var handler = new FileHandler("jass_client-%u.%g.log");
            handler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.setProperty("sun.java2d.opengl", "true");

        var ui = args.length > 0 && args[0].compareTo("original") == 0 ? new OriginalUi() : new ModernUi();
        ui.showUi(true);

        ConnectionInfo dialogInfo;
        do {
            dialogInfo = ui.showConnectDialog();
            if (dialogInfo.ok) {
                if (dialogInfo.local) {
                    // local game
                    ui.setPlayer(new ClientPlayer(0, dialogInfo.name), 0);
                    var player = new JassPlayer(ui, 0, dialogInfo.name, -1);
                    player.setName(dialogInfo.name);
                    var gameController = new GameController(0);
                    try {
                        gameController.addPlayer(player);
                    } catch (PlayerLeftExpection e) {
                        throw new RuntimeException(e);
                    }
                    for (int i=1; i<4; ++i) {
                        var aip = new ArtificialPlayer(i, "iBerte"+i);
                        try {
                            gameController.addPlayer(aip);
                        } catch (PlayerLeftExpection e) {
                            throw new RuntimeException(e);
                        }
                    }
                    gameController.start();
                    try {
                        gameController.join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    // network game
                    var network = new ClientNetwork();
                    var connectionInfo = network.connect(dialogInfo.hostname, dialogInfo.gameNumber, dialogInfo.name);
                    if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
                        switch (connectionInfo.error) {
                            case ConnectionError.SERVER_UNREACHABLE:
                                ui.showMessage("Erreur", "La connection a échoué.", JOptionPane.ERROR_MESSAGE);
                                break;
                            case ConnectionError.GAME_FULL:
                                ui.showMessage("Erreur", "Ce jeu est déja complet.", JOptionPane.ERROR_MESSAGE);
                                break;
                            case ConnectionError.UNKNOWN_GAME:
                                ui.showMessage("Erreur", "Le jeu " + connectionInfo.gameId + " n'existe pas.", JOptionPane.ERROR_MESSAGE);
                                break;
                        }
                    } else {
                        ui.setPlayer(new ClientPlayer(connectionInfo.playerId, dialogInfo.name), 0);
                        var player = new JassPlayer(ui, connectionInfo.playerId, dialogInfo.name, connectionInfo.gameId);
                        player.setName(dialogInfo.name);
                        var gameController = new RemoteController(player, network);
                        var controllerThread = new Thread(gameController, "controller-thread");
                        controllerThread.start();
                        try {
                            controllerThread.join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        } while(dialogInfo.ok);
    }
}
