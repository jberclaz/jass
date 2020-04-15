package com.leflat.jass.test;

import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Team;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.RemotePlayer;
import com.leflat.jass.server.ServerNetwork;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TestSingleClient {
    private RemotePlayer mainPlayer;
    private List<BasePlayer> players = new ArrayList<>();
    private Team[] teams = new Team[2];

    public static final int PORT = 23107;

    public TestSingleClient() throws IOException, PlayerLeftExpection {
        ServerSocket serverSocket;
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Started test server on port " + PORT);
            clientSocket = serverSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        var network = new ServerNetwork(clientSocket);
        network.receiveRawMessage();
        network.sendMessage(String.valueOf(12345));

        mainPlayer = new RemotePlayer(0, new ServerNetwork(clientSocket));

        var clientNetworkFactory = new MockNetworkFactory(1);
        var player1 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.3f));
        player1.setName("Berte");
        var player2 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.3f));
        player2.setName("GC");
        var player3 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.3f));
        player3.setName("Pischus");

        var game = new GameController(1234);

        game.addPlayer(mainPlayer);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);

        game.run();
    }

    public static void main(String[] args) {
        try {
            new TestSingleClient();
        } catch (IOException | PlayerLeftExpection e) {
            e.printStackTrace();
        }
    }
}
