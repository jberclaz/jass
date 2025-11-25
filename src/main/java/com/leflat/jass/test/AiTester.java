package com.leflat.jass.test;

import com.leflat.jass.server.*;

import java.io.FileNotFoundException;

public class AiTester {
    public static void main(String[] args) throws PlayerLeftExpection, InterruptedException, FileNotFoundException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        game.playKGames(10);
        for (int i = 0; i < 4; i++) {
            String name = i % 2 == 0 ? "mc-" + i : "former-" + i;
            var player = new ArtificialPlayer(i, name, i % 2 == 0 ? new MonteCarloStrategy(100) : new TransformersStrategy("target/classes/model/jassformer.onnx"), true);
            game.addPlayer(player);
        }
        game.start();
        game.join();
    }
}
