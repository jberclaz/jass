package com.leflat.jass.test;

import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;

import java.io.IOException;

public class TestArtificialPlayer {
    public TestArtificialPlayer() throws IOException, PlayerLeftExpection {
/*
        var clientNetworkFactory = new MockNetworkFactory(0);
        var randomPlayer = new JassPlayer(clientNetworkFactory, new MockUiFactory(0, 100));
        randomPlayer.setName("Random");
        var player1 = new ArtificialPlayer(1, "Berte");
        var player2 = new ArtificialPlayer(2, "GC");
        var player3 = new ArtificialPlayer(3, "Pischus");

        var game = new GameController(1234);
        game.setNoWait(true);

        game.addPlayer(randomPlayer);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);

        game.start();

 */
    }

    public static void main(String[] args) {
        try {
            new TestArtificialPlayer();
        } catch (IOException | PlayerLeftExpection e) {
            e.printStackTrace();
        }
    }
}
