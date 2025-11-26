package com.leflat.jass.test;

import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.server.*;

import java.io.FileNotFoundException;

public class AiTester {
    public static void main(String[] args) throws PlayerLeftExpection, InterruptedException, FileNotFoundException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        game.playKGames(10);
        IJassStrategy firstStrategy = new MonteCarloStrategy(100);
        IJassStrategy secondStrategy = args.length == 0 ? new TransformersStrategy() : new TransformersStrategy(args[0]);
        for (int i = 0; i < 4; i++) {
            String name = i % 2 == 0 ? firstStrategy + "-" + i : secondStrategy + "-" + i;
            var player = new ArtificialPlayer(i, name, i % 2 == 0 ? firstStrategy : secondStrategy, true);
            game.addPlayer(player);
        }
        game.start();
        game.join();
    }
}
