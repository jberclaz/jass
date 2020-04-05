package com.leflat.jass.server;

import com.leflat.jass.common.ConnectionError;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class NetworkListener extends Thread {
    private ServerSocket serverSocket;
    private Map<Integer, GameController> games = new HashMap<>();
    private boolean running = false;

    public NetworkListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);

        // TODO: remove (DEBUG)
        games.put(0, new GameController(0));
    }

    @Override
    public void run() {
        System.out.println("Jass server running on port " + serverSocket.getLocalPort());
        running = true;
        while (running) {
            try {
                var clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleNewConnection(Socket clientSocket) throws IOException {
        var rpc = new Rpc(clientSocket);

        try {
            var game = selectGame(rpc);
            if (game == null) {
                return;
            }

            var newPlayer = new RemotePlayer(game.getNbrPlayers(), rpc);
            game.addPlayer(newPlayer);

            if (game.fullGame()) {
                game.start();
            }
        } catch (PlayerLeftExpection ignored) {
            System.err.println("Error: client left while attempting to connect");
        }
    }

    private GameController selectGame(Rpc rpc) throws PlayerLeftExpection {
        GameController game;
        String connectionMessage = rpc.receiveRawMessage();
        int gameId = Integer.parseInt(connectionMessage);
        if (gameId < 0) {
            int newGameId = getRandomGameId();
            rpc.sendMessage(String.valueOf(newGameId));
            game = new GameController(newGameId);
            games.put(newGameId, game);
        } else {
            if (!games.containsKey(gameId)) {
                rpc.sendMessage(String.valueOf(ConnectionError.UNKNOWN_GAME));
                System.err.println("Error: unknown game id " + gameId);
                return null;
            }
            game = games.get(gameId);
            if (game.fullGame()) {
                rpc.sendMessage(String.valueOf(ConnectionError.GAME_FULL));
                System.err.println("Error: attempting to enter full game " + gameId);
                return null;
            }
            rpc.sendMessage(String.valueOf(gameId));
        }
        return game;
    }

    private int getRandomGameId() {
        Random rnd = new Random();
        return rnd.nextInt(999999);
    }
}
