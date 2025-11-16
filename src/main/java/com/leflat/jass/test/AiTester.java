package com.leflat.jass.test;

import com.leflat.jass.server.*;

import java.io.FileNotFoundException;

public class AiTester {
    public static void main(String[] aregs) throws PlayerLeftExpection, InterruptedException, FileNotFoundException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        game.playKGames(10);
        for (int i = 0; i < 4; i++) {
            String name = i % 2 == 0 ? "heur-" + i : "former-" + i;
            var player = new ArtificialPlayer(i, name, i % 2 == 0 ? new HeuristicStrategy() : new TransformersStrategy(), true);
            game.addPlayer(player);
        }
        game.start();
        game.join();
    }
}
