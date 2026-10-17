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
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Plays a transformers model against MonteCarlo players, at one or several MonteCarlo strengths. The model can also
 * be a MonteCarlo player ({@code mc:<strength>}), e.g. to measure how strong the teacher of the supervised training is.
 * <p>
 * By default, the games are played in pairs with the same deals (duplicate): each deal is played once with the model
 * in team 1 and once in team 0, so that both sides get the same cards and most of the luck cancels out. Reports the
 * win rate and the point difference per game (model - MonteCarlo), with their 95% confidence intervals.
 */
@Command(name = "AiTester", mixinStandardHelpOptions = true, version = "1.0")
public class AiTester implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1",
            description = "Model path (default: the classpath model), or mc:<strength> to play MonteCarlo instead")
    private String modelPath;

    @Option(names = {"-g", "--games"}, defaultValue = "10",
            description = "Number of games per strength, rounded up to an even number with duplicate deals (default: ${DEFAULT-VALUE})")
    private int games;

    @Option(names = {"-s", "--strength"}, split = ",", defaultValue = "100",
            description = "MonteCarlo strength, or comma-separated strengths (default: ${DEFAULT-VALUE})")
    private List<Integer> strengths;

    @Option(names = "--no-duplicate", description = "Independent random deals instead of pairs of games with the same deals")
    private boolean independent;

    @Option(names = "--seed", description = "Seed of the deals, to replay the same games (default: random)")
    private Long seed;

    @Option(names = {"-r", "--results"}, description = "Also write the results as JSON to this file")
    private File resultsFile;

    @Option(names = {"-v", "--verbose"}, description = "Show the game logs (moves, announcements) instead of a progress bar")
    private boolean verbose;

    /** One game, from the point of view of the model. */
    record Game(boolean won, int pointDifference) {
    }

    /** Mean and 95% confidence interval. */
    record Estimate(double mean, double low, double high) {
        /** Normal approximation over independent samples. */
        static Estimate of(List<Double> samples) {
            int n = samples.size();
            if (n == 0) return new Estimate(Double.NaN, Double.NaN, Double.NaN);
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = n < 2 ? 0 : samples.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / (n - 1);
            double margin = 1.96 * Math.sqrt(variance / n);
            return new Estimate(mean, mean - margin, mean + margin);
        }

        /** Wilson score interval of a proportion, better than the normal approximation for few games. */
        static Estimate wilson(int successes, int n) {
            if (n == 0) return new Estimate(Double.NaN, Double.NaN, Double.NaN);
            double z = 1.96, p = (double) successes / n;
            double center = (p + z * z / (2 * n)) / (1 + z * z / n);
            double margin = z / (1 + z * z / n) * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n));
            return new Estimate(p, Math.max(0, center - margin), Math.min(1, center + margin));
        }

        Estimate clamp(double min, double max) {
            return new Estimate(mean, Math.max(min, low), Math.min(max, high));
        }
    }

    record Result(int strength, boolean duplicate, int games, int wins, Estimate winRate, Estimate points) {
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
        IJassStrategy candidate;
        if (modelPath != null && modelPath.startsWith("mc:")) {
            candidate = new MonteCarloStrategy(Integer.parseInt(modelPath.substring(3)));
        } else {
            var transformers = modelPath == null ? new TransformersStrategy() : new TransformersStrategy(modelPath);
            if (!transformers.isModelLoaded()) {
                // otherwise the transformers players silently fall back to MonteCarlo
                System.err.println("Unable to load model " + (modelPath == null ? "from the classpath" : modelPath));
                return 1;
            }
            candidate = transformers;
        }

        List<Result> results = new ArrayList<>();
        for (int strength : strengths) {
            results.add(play(new MonteCarloStrategy(strength), candidate, strength));
        }

        if (results.size() > 1) {
            System.out.println();
            System.out.println(candidate + " (95% confidence intervals):");
            for (var result : results) {
                System.out.println("  vs " + format(result));
            }
        }
        if (resultsFile != null) {
            Files.writeString(resultsFile.toPath(), toJson(results));
        }
        return 0;
    }

    private Result play(IJassStrategy monteCarlo, IJassStrategy candidate, int strength)
            throws PlayerLeftExpection, InterruptedException {
        boolean duplicate = !independent;
        int gamesPerPass = duplicate ? (games + 1) / 2 : games;
        int passes = duplicate ? 2 : 1;
        long dealSeed = seed != null ? seed : new Random().nextLong();

        System.out.printf("Playing %,d games%s | %s vs %s%n", gamesPerPass * passes,
                duplicate ? " (" + gamesPerPass + " deals, each played from both sides)" : "", candidate, monteCarlo);

        List<List<Game>> played = new ArrayList<>();
        var progress = verbose ? null : new ProgressBar(gamesPerPass * passes, "games");
        if (progress != null) {
            progress.update(0, "");
        }
        for (int pass = 0; pass < passes; pass++) {
            // the model plays in team 1 (odd seats), then in team 0 with the same deals
            int candidateTeam = pass == 0 ? 1 : 0;
            var game = new GameController(0);
            game.setNoWait(true);
            game.enableTeamSelection(false);
            game.setDealSeed(dealSeed);
            for (int i = 0; i < 4; i++) {
                var strategy = i % 2 == candidateTeam ? candidate : monteCarlo;
                game.addPlayer(new ArtificialPlayer(i, strategy + "-" + i, strategy, true));
            }

            List<Game> passGames = new ArrayList<>();
            played.add(passGames);
            game.setGameDoneListener(() -> {
                var teams = game.getTeams();
                // same rule as GameController: team 0 wins if it reached the winning score
                boolean won = teams[0].hasWon() == (candidateTeam == 0);
                passGames.add(new Game(won, teams[candidateTeam].getScore() - teams[1 - candidateTeam].getScore()));
                if (progress != null) {
                    var all = played.stream().flatMap(List::stream).toList();
                    long wins = all.stream().filter(Game::won).count();
                    double points = all.stream().mapToInt(Game::pointDifference).average().orElse(0);
                    progress.update(all.size(), String.format(Locale.ROOT, "%s %d : %d %s | %+.0f points/game",
                            candidate, wins, all.size() - wins, monteCarlo, points));
                }
            });

            game.playKGames(gamesPerPass);
            game.start();
            game.join();
        }

        var result = summarize(strength, duplicate, played);
        System.out.println(candidate + " vs " + format(result));
        return result;
    }

    private static Result summarize(int strength, boolean duplicate, List<List<Game>> played) {
        // the samples are the deal pairs with duplicate deals (the two games of a pair are not independent)
        int units = played.stream().mapToInt(List::size).min().orElse(0);
        List<Double> winSamples = new ArrayList<>();
        List<Double> pointSamples = new ArrayList<>();
        int games = 0, wins = 0;
        for (int k = 0; k < units; k++) {
            double unitWins = 0, unitPoints = 0;
            for (var pass : played) {
                var game = pass.get(k);
                unitWins += game.won() ? 1 : 0;
                unitPoints += game.pointDifference();
                games++;
                wins += game.won() ? 1 : 0;
            }
            winSamples.add(unitWins / played.size());
            pointSamples.add(unitPoints / played.size());
        }
        // the normal approximation has no width when all the pairs have the same result (e.g. all lost)
        boolean allEqual = winSamples.stream().distinct().count() <= 1;
        var winRate = duplicate && !allEqual ? Estimate.of(winSamples).clamp(0, 1) : Estimate.wilson(wins, games);
        return new Result(strength, duplicate, games, wins, winRate, Estimate.of(pointSamples));
    }

    private static String format(Result result) {
        var w = result.winRate();
        var p = result.points();
        return String.format(Locale.ROOT, "MonteCarlo-%d: won %.1f%% [%.1f%%, %.1f%%] | %+.0f [%+.0f, %+.0f] points/game | %d games%s",
                result.strength(), 100 * w.mean(), 100 * w.low(), 100 * w.high(), p.mean(), p.low(), p.high(),
                result.games(), result.duplicate() ? " (" + result.games() / 2 + " deal pairs)" : "");
    }

    private String toJson(List<Result> results) {
        String model = modelPath == null ? "classpath" : modelPath.replace("\\", "\\\\").replace("\"", "\\\"");
        return results.stream().map(r -> String.format(Locale.ROOT,
                "    {\"strength\": %d, \"duplicate\": %b, \"games\": %d, \"wins\": %d, "
                        + "\"win_rate\": %.4f, \"ci_low\": %.4f, \"ci_high\": %.4f, "
                        + "\"points\": %.2f, \"points_ci_low\": %.2f, \"points_ci_high\": %.2f}",
                r.strength(), r.duplicate(), r.games(), r.wins(),
                r.winRate().mean(), r.winRate().low(), r.winRate().high(),
                r.points().mean(), r.points().low(), r.points().high())
        ).collect(Collectors.joining(",\n", "{\n  \"model\": \"" + model + "\",\n  \"results\": [\n", "\n  ]\n}\n"));
    }
}
