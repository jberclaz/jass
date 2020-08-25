package com.leflat.jass.test;

import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;

import java.io.IOException;

public class TestComputerPlayer {

    public TestComputerPlayer() throws PlayerLeftExpection {

        var player1 = new ArtificialPlayer(0, "GC");
        var player2 = new ArtificialPlayer(1, "Berte");
        var player3 = new ArtificialPlayer(2, "Pischus");
        var player4 = new ArtificialPlayer(3, "Wein");

        var game = new GameController(1234);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        game.start();
    }

    public static void main(String[] args) {
        try {
            new TestComputerPlayer();
        } catch (PlayerLeftExpection e) {
            e.printStackTrace();
        }
    }
}
