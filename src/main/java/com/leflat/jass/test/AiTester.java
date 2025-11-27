package com.leflat.jass.test;

import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.server.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileNotFoundException;
import java.util.concurrent.Callable;

@Command(name = "AiTester", mixinStandardHelpOptions = true, version = "1.0")
public class AiTester implements Callable<Void> {

    @Parameters(index = "0", arity = "0..1", description = "Model path")
    private String modelPath;

    @Option(names = {"-g", "--games"}, defaultValue = "10", description = "Number of games (default: ${DEFAULT-VALUE})")
    private int games;

    @Option(names = {"-s", "--strength"}, defaultValue = "100", description = "MonteCarlo strength (default: ${DEFAULT-VALUE})")
    private int strength;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AiTester()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Void call() throws PlayerLeftExpection, InterruptedException, FileNotFoundException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);

        IJassStrategy firstStrategy = new MonteCarloStrategy(strength);
        IJassStrategy secondStrategy = modelPath == null ? new TransformersStrategy() : new TransformersStrategy(modelPath);

        for (int i = 0; i < 4; i++) {
            String name = i % 2 == 0 ? firstStrategy + "-" + i : secondStrategy + "-" + i;
            var player = new ArtificialPlayer(i, name, i % 2 == 0 ? firstStrategy : secondStrategy, true);
            game.addPlayer(player);
        }

        System.out.printf("Playing %,d games | %s vs %s\n", games, firstStrategy, secondStrategy);

        game.playKGames(games);
        game.start();
        game.join();
        return null;
    }

    private static String baseName(String path) {
        if (path == null) return "";
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (sep >= 0 ? path.substring(sep + 1) : path).replaceFirst("\\.pt$", "");
    }
}