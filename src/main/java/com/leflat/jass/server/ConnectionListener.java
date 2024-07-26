package com.leflat.jass.server;

import com.leflat.jass.common.ConnectionError;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionListener extends Thread {
    private final ServerSocket serverSocket;
    private final Map<Integer, GameController> games = new HashMap<>();
    private boolean running = false;
    private boolean testMode = false;
    private final static Logger LOGGER = Logger.getLogger(GameController.class.getName());

    public ConnectionListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public ConnectionListener(int port, boolean testMode) throws IOException {
        this(port);
        this.testMode = testMode;
    }

    public void terminate() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("Jass server running on port " + serverSocket.getLocalPort());
        LOGGER.info("Jass server running on port " + serverSocket.getLocalPort());
        running = true;
        while (running) {
            try {
                var clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error while handling connection", e);
            }
        }
        for (var game : games.values()) {
            try {
                game.join();
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Error while terminating game", e);
            }
        }
    }

    private void handleNewConnection(Socket clientSocket) throws IOException {
        var network = new ServerNetwork(clientSocket);

        try {
            var game = selectGame(network);
            if (game == null) {
                return;
            }

            var newPlayer = new RemotePlayer(game.getNbrPlayers(), network);
            game.addPlayer(newPlayer);

            if (game.isGameFull()) {
                game.start();
            }
        } catch (PlayerLeftExpection ignored) {
            LOGGER.warning("Error: client left while attempting to connect");
        }
    }

    private GameController selectGame(ServerNetwork network) throws PlayerLeftExpection {
        GameController game;
        String connectionMessage = network.receiveRawMessage();
        int gameId = Integer.parseInt(connectionMessage);
        if (gameId < 0) {
            int newGameId = getRandomGameId();
            network.sendMessage(String.valueOf(newGameId));
            game = new GameController(newGameId);
            if (testMode) {
                game.setNoWait(true);
            }
            games.put(newGameId, game);
        } else {
            if (!games.containsKey(gameId)) {
                network.sendMessage(String.valueOf(ConnectionError.UNKNOWN_GAME));
                LOGGER.warning("Error: unknown game id " + gameId);
                return null;
            }
            game = games.get(gameId);
            if (game.isGameFull()) {
                network.sendMessage(String.valueOf(ConnectionError.GAME_FULL));
                LOGGER.warning("Error: attempting to enter full game " + gameId);
                return null;
            }
            network.sendMessage(String.valueOf(gameId));
        }
        return game;
    }

    private int getRandomGameId() {
        Random rnd = new Random();
        return rnd.nextInt(999999);
    }
}
