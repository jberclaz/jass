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
            String name = i % 2 == 0 ? "former-" + i : "mc-" + i;
            var player = new ArtificialPlayer(i, name, i % 2 == 0 ? new TransformersStrategy() : new MonteCarloStrategy(1000), true);
            player.extractTransformersTokens("tokens_file" + i + ".dat");
            game.addPlayer(player);
        }
        game.start();
        game.join();
    }
}
