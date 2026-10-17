package com.leflat.jass.test;

import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.server.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Plays a transformers model (team 1) against MonteCarlo players (team 0), at one or several MonteCarlo strengths.
 */
@Command(name = "AiTester", mixinStandardHelpOptions = true, version = "1.0")
public class AiTester implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Model path")
    private String modelPath;

    @Option(names = {"-g", "--games"}, defaultValue = "10", description = "Number of games per strength (default: ${DEFAULT-VALUE})")
    private int games;

    @Option(names = {"-s", "--strength"}, split = ",", defaultValue = "100",
            description = "MonteCarlo strength, or comma-separated strengths (default: ${DEFAULT-VALUE})")
    private List<Integer> strengths;

    @Option(names = {"-r", "--results"}, description = "Also write the results as JSON to this file")
    private File resultsFile;

    @Option(names = {"-v", "--verbose"}, description = "Show the game logs (moves, announcements) instead of a progress bar")
    private boolean verbose;

    record Result(int strength, int games, int wins) {
        double winRate() {
            return games > 0 ? (double) wins / games : Double.NaN;
        }

        /** Wilson score interval: 95% confidence interval of the win rate. */
        double[] confidenceInterval() {
            if (games == 0) return new double[]{Double.NaN, Double.NaN};
            double z = 1.96, p = winRate(), n = games;
            double center = (p + z * z / (2 * n)) / (1 + z * z / n);
            double margin = z / (1 + z * z / n) * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n));
            return new double[]{Math.max(0, center - margin), Math.min(1, center + margin)};
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AiTester()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws PlayerLeftExpection, InterruptedException, IOException {
        if (!verbose) {
            ProgressBar.hideInfoLogs();
        }
        var transformers = modelPath == null ? new TransformersStrategy() : new TransformersStrategy(modelPath);
        if (!transformers.isModelLoaded()) {
            // otherwise the transformers players silently fall back to MonteCarlo
            System.err.println("Unable to load model " + (modelPath == null ? "from the classpath" : modelPath));
            return 1;
        }

        List<Result> results = new ArrayList<>();
        for (int strength : strengths) {
            results.add(play(new MonteCarloStrategy(strength), transformers, strength));
        }

        if (results.size() > 1) {
            System.out.println();
            System.out.println("Transformers win rate (95% confidence interval):");
            for (var result : results) {
                System.out.println("  vs " + format(result));
            }
        }
        if (resultsFile != null) {
            Files.writeString(resultsFile.toPath(), toJson(results));
        }
        return 0;
    }

    private Result play(IJassStrategy monteCarlo, IJassStrategy transformers, int strength)
            throws PlayerLeftExpection, InterruptedException {
        var game = new GameController(0);
        game.setNoWait(true);
        game.enableTeamSelection(false);
        for (int i = 0; i < 4; i++) {
            var strategy = i % 2 == 0 ? monteCarlo : transformers;
            game.addPlayer(new ArtificialPlayer(i, strategy + "-" + i, strategy, true));
        }

        System.out.printf("Playing %,d games | team 0: %s vs team 1: %s%n", games, monteCarlo, transformers);
        String[] teamNames = {monteCarlo.toString(), transformers.toString()};
        if (!verbose) {
            var progress = new ProgressBar(games, "games");
            int[] gamesDone = {0};
            game.setGameDoneListener(() -> progress.update(++gamesDone[0],
                    teamNames[0] + " " + game.getMatchWinCount(0) + " : " + game.getMatchWinCount(1) + " " + teamNames[1]));
            progress.update(0, "");
        }

        game.playKGames(games);
        game.start();
        game.join();

        // counts the games actually played, in case the game stopped early
        int played = game.getMatchWinCount(0) + game.getMatchWinCount(1);
        for (int team = 0; team < 2; team++) {
            int wins = game.getMatchWinCount(team);
            System.out.printf("Team %d (%s): won %d of %d games (%.0f%%)%n",
                    team, teamNames[team], wins, played, played > 0 ? 100.0 * wins / played : 0.0);
        }
        var result = new Result(strength, played, game.getMatchWinCount(1));
        System.out.println("Transformers vs " + format(result));
        return result;
    }

    private static String format(Result result) {
        var ci = result.confidenceInterval();
        return String.format(Locale.ROOT, "MonteCarlo-%d: %.1f%% [%.1f%%, %.1f%%] over %d games",
                result.strength(), 100 * result.winRate(), 100 * ci[0], 100 * ci[1], result.games());
    }

    private String toJson(List<Result> results) {
        String model = modelPath == null ? "classpath" : modelPath.replace("\\", "\\\\").replace("\"", "\\\"");
        return results.stream().map(r -> {
            var ci = r.confidenceInterval();
            return String.format(Locale.ROOT,
                    "    {\"strength\": %d, \"games\": %d, \"wins\": %d, \"win_rate\": %.4f, \"ci_low\": %.4f, \"ci_high\": %.4f}",
                    r.strength(), r.games(), r.wins(), r.winRate(), ci[0], ci[1]);
        }).collect(Collectors.joining(",\n", "{\n  \"model\": \"" + model + "\",\n  \"results\": [\n", "\n  ]\n}\n"));
    }
}
