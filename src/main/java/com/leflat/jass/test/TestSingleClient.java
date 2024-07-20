package com.leflat.jass.test;

import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.server.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TestSingleClient {
    private RemotePlayer mainPlayer;

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


        //var player1 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.8f));
        var player1 = new ArtificialPlayer(1, "Berte");
        //player1.setName("Berte");
        //var player2 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.8f));
        var player2 = new ArtificialPlayer(2, "GC");
        //player2.setName("GC");
        //var player3 = new JassPlayer(clientNetworkFactory, new MockUiFactory(.8f));
        //player3.setName("Pischus");
        var player3 = new ArtificialPlayer(3, "Pischus");

        var game = new GameController(1234);

        game.addPlayer(mainPlayer);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);

        game.start();
    }

    public static void main(String[] args) {
        try {
            new TestSingleClient();
        } catch (IOException | PlayerLeftExpection e) {
            e.printStackTrace();
        }
    }
}
