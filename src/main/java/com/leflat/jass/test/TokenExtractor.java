package com.leflat.jass.test;

import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.MonteCarloStrategy;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Plays games between Monte Carlo players and records, for each seat, the transformer tokens of every decision
 * followed by the chosen action (one file per seat, in chronological order).
 */
@Command(name = "TokenExtractor", mixinStandardHelpOptions = true, version = "1.0")
public class TokenExtractor implements Callable<Integer> {

    @Option(names = {"-o", "--output"}, defaultValue = ".", description = "Output directory (default: ${DEFAULT-VALUE})")
    private File outputDir;

    @Option(names = {"-g", "--games"}, defaultValue = "20", description = "Number of games (default: ${DEFAULT-VALUE})")
    private int games;

    @Option(names = {"-s", "--strength"}, defaultValue = "100", description = "MonteCarlo strength (default: ${DEFAULT-VALUE})")
    private int strength;

    @Option(names = {"-n", "--name"}, description = "Run name, used as file prefix (default: current date and time)")
    private String runName;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TokenExtractor()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (runName == null) {
            runName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            throw new IOException("Unable to create output directory " + outputDir);
        }
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var file = new File(outputDir, runName + "_seat" + i + ".dat");
            if (file.exists()) {
                System.err.println("File " + file + " already exists, choose another run name or output directory");
                return 1;
            }
            files.add(file);
        }

        System.out.printf("Playing %,d games with MonteCarlo strength %d, writing %s/%s_seat*.dat%n",
                games, strength, outputDir, runName);

        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        game.playKGames(games);
        List<ArtificialPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var player = new ArtificialPlayer(i, String.valueOf(i), new MonteCarloStrategy(strength), true);
            player.extractTransformersTokens(files.get(i).getPath());
            game.addPlayer(player);
            players.add(player);
        }
        game.start();
        game.join();
        for (var player : players) {
            player.close();
        }
        return 0;
    }
}
