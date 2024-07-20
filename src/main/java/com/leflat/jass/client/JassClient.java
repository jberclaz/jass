package com.leflat.jass.client;

import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IJassUi;
import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.*;

public class JassClient {
    private static void localGame(IJassUi ui, ConnectionInfo dialogInfo) {
        ui.setPlayer(new ClientPlayer(0, dialogInfo.name), 0);
        var player = new JassPlayer(ui, 0, dialogInfo.name, -1);
        player.setName(dialogInfo.name);
        var gameController = new GameController(0);
        try {
            gameController.addPlayer(player);
        } catch (PlayerLeftExpection e) {
            throw new RuntimeException(e);
        }
        for (int i = 1; i < 4; ++i) {
            var aip = new ArtificialPlayer(i, "iBerte" + i);
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

    private static void networkGame(IJassUi ui, ConnectionInfo dialogInfo) {
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
                    ui.showMessage("Erreur", "Le jeu " + dialogInfo.gameNumber + " n'existe pas.", JOptionPane.ERROR_MESSAGE);
                    break;
            }
        } else {
            ui.setPlayer(new ClientPlayer(connectionInfo.playerId, dialogInfo.name), 0);
            var player = new JassPlayer(ui, connectionInfo.playerId, dialogInfo.name, connectionInfo.gameId);
            player.setName(dialogInfo.name);
            var gameController = new RemoteController(player, network);
            var controllerThread = new Thread(gameController, "controller-thread");

            var aiControllerThreads = new LinkedList<Thread>();
            if (!dialogInfo.joinExistingGame && dialogInfo.nbrArtificialPlayers > 0) {
                for (int p=1; p<= dialogInfo.nbrArtificialPlayers; ++p) {
                    var aiName = "iBerte" + p;
                    var aiPlayer = new ArtificialPlayer(p, aiName);
                    var aiNetwork = new ClientNetwork();
                    aiNetwork.connect(dialogInfo.hostname, connectionInfo.gameId, aiName);
                    if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {

                    }
                    var aiGameController = new RemoteController(aiPlayer, aiNetwork);
                    var aiControllerThread = new Thread(aiGameController, "ai-controller-"+p);
                    aiControllerThreads.add(aiControllerThread);
                }
            }

            controllerThread.start();
            for (var aiThread : aiControllerThreads) {
                aiThread.start();
            }
            try {
                controllerThread.join();
                for (var aiThread : aiControllerThreads) {
                    aiThread.join();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

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
                    localGame(ui, dialogInfo);
                } else {
                    // network game
                    networkGame(ui, dialogInfo);
                }
            }
        } while (dialogInfo.ok);
    }
}
