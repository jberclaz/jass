package com.leflat.jass.test;

import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;

import java.io.FileNotFoundException;

public class AiTester {
    public static void main(String[] aregs) throws PlayerLeftExpection, InterruptedException, FileNotFoundException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        game.playKGames(100);
        for (int i = 0; i < 4; i++) {
            int strength = i % 2 == 0 ? 100 : -1;
            String name = i % 2 == 0 ? "mc-" + i : "nn-" + i;
            var player = new ArtificialPlayer(i, name, strength, true);
            player.extractTransformersTokens("tokens_file" + i + ".dat");
            game.addPlayer(player);
        }
        game.start();
        game.join();
    }
}
