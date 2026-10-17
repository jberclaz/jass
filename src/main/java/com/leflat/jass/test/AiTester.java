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
 * By default, plays single hands (from a score of 0 - 0) rather than games to 1500 points: the hands are independent,
 * and a game adds the luck of several hands and a threshold. The hands (or games) are played in pairs with the same
 * deals (duplicate): each deal is played once with the model in team 1 and once in team 0, so that both sides get the
 * same cards and most of the luck cancels out. Reports the point difference per hand (model - MonteCarlo) and the
 * share of hands won, with their 95% confidence intervals.
 */
@Command(name = "AiTester", mixinStandardHelpOptions = true, version = "1.0")
public class AiTester implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1",
            description = "Model path (default: the classpath model), or mc:<strength> to play MonteCarlo instead")
    private String modelPath;

    @Option(names = {"-n", "--hands"},
            description = "Number of hands per strength, rounded up to an even number with duplicate deals (default: 200)")
    private Integer hands;

    @Option(names = {"-g", "--games"}, description = "Play this number of full games to 1500 points instead of hands")
    private Integer games;

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

    /** One hand or game, from the point of view of the model: won 1, tie 0.5 (single hands only) or lost 0. */
    record Game(double won, int pointDifference) {
    }

    /** Mean and 95% confidence interval. */
    record Estimate(double mean, double low, double high) {
        /** Normal approximation over independent samples. */
        static Estimate of(List<Double> samples) {
            int n = samples.size();
            if (n == 0) return new Estimate(Double.NaN, Double.NaN, Double.NaN);
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            if (n < 2) return new Estimate(mean, Double.NaN, Double.NaN);  // no estimate of the spread
            double variance = samples.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / (n - 1);
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

    record Result(int strength, String unit, boolean duplicate, int count, double wins, Estimate winRate, Estimate points) {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AiTester()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws PlayerLeftExpection, InterruptedException, IOException {
        if (hands != null && games != null) {
            System.err.println("Give either --hands or --games, not both");
            return 2;
        }
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
        boolean singleHands = games == null;
        int count = singleHands ? (hands != null ? hands : 200) : games;
        String unit = singleHands ? "hand" : "game";
        int gamesPerPass = duplicate ? (count + 1) / 2 : count;
        int passes = duplicate ? 2 : 1;
        long dealSeed = seed != null ? seed : new Random().nextLong();

        System.out.printf("Playing %,d %ss%s | %s vs %s%n", gamesPerPass * passes, unit,
                duplicate ? " (" + gamesPerPass + " deals, each played from both sides)" : "", candidate, monteCarlo);

        List<List<Game>> played = new ArrayList<>();
        var progress = verbose ? null : new ProgressBar(gamesPerPass * passes, unit + "s");
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
            game.setSingleHandGames(singleHands);
            for (int i = 0; i < 4; i++) {
                var strategy = i % 2 == candidateTeam ? candidate : monteCarlo;
                game.addPlayer(new ArtificialPlayer(i, strategy + "-" + i, strategy, true));
            }

            List<Game> passGames = new ArrayList<>();
            played.add(passGames);
            game.setGameDoneListener(() -> {
                var teams = game.getTeams();
                int difference = teams[candidateTeam].getScore() - teams[1 - candidateTeam].getScore();
                double won = singleHands
                        ? Math.signum(difference) / 2 + 0.5
                        // same rule as GameController: team 0 wins if it reached the winning score
                        : (teams[0].hasWon() == (candidateTeam == 0) ? 1 : 0);
                passGames.add(new Game(won, difference));
                if (progress != null) {
                    var all = played.stream().flatMap(List::stream).toList();
                    double wins = all.stream().mapToDouble(Game::won).sum();
                    double points = all.stream().mapToInt(Game::pointDifference).average().orElse(0);
                    progress.update(all.size(), String.format(Locale.ROOT, "%s %+.1f points/%s | won %.0f%%",
                            candidate, points, unit, 100 * wins / all.size()));
                }
            });

            game.playKGames(gamesPerPass);
            game.start();
            game.join();
        }

        var result = summarize(strength, unit, duplicate, played);
        System.out.println(candidate + " vs " + format(result));
        return result;
    }

    private static Result summarize(int strength, String unit, boolean duplicate, List<List<Game>> played) {
        // the samples are the deal pairs with duplicate deals (the two hands of a pair are not independent)
        int units = played.stream().mapToInt(List::size).min().orElse(0);
        List<Double> winSamples = new ArrayList<>();
        List<Double> pointSamples = new ArrayList<>();
        int count = 0;
        double wins = 0;
        for (int k = 0; k < units; k++) {
            double unitWins = 0, unitPoints = 0;
            for (var pass : played) {
                var game = pass.get(k);
                unitWins += game.won();
                unitPoints += game.pointDifference();
                count++;
                wins += game.won();
            }
            winSamples.add(unitWins / played.size());
            pointSamples.add(unitPoints / played.size());
        }
        // the normal approximation has no width when all the pairs have the same result (e.g. all lost)
        boolean allEqual = winSamples.stream().distinct().count() <= 1;
        var winRate = duplicate && !allEqual
                ? Estimate.of(winSamples).clamp(0, 1)
                : Estimate.wilson((int) Math.round(wins), count);
        return new Result(strength, unit, duplicate, count, wins, winRate, Estimate.of(pointSamples));
    }

    private static String format(Result result) {
        var w = result.winRate();
        var p = result.points();
        String unit = result.unit();
        return String.format(Locale.ROOT, "MonteCarlo-%d: %+.1f [%+.1f, %+.1f] points/%s | won %.1f%% [%.1f%%, %.1f%%] | %d %ss%s",
                result.strength(), p.mean(), p.low(), p.high(), unit, 100 * w.mean(), 100 * w.low(), 100 * w.high(),
                result.count(), unit, result.duplicate() ? " (" + result.count() / 2 + " deal pairs)" : "");
    }

    private String toJson(List<Result> results) {
        String model = modelPath == null ? "classpath" : modelPath.replace("\\", "\\\\").replace("\"", "\\\"");
        return results.stream().map(r -> String.format(Locale.ROOT,
                "    {\"strength\": %d, \"unit\": \"%s\", \"duplicate\": %b, \"count\": %d, \"wins\": %.1f, "
                        + "\"win_rate\": %.4f, \"ci_low\": %.4f, \"ci_high\": %.4f, "
                        + "\"points\": %.2f, \"points_ci_low\": %.2f, \"points_ci_high\": %.2f}",
                r.strength(), r.unit(), r.duplicate(), r.count(), r.wins(),
                r.winRate().mean(), r.winRate().low(), r.winRate().high(),
                r.points().mean(), r.points().low(), r.points().high())
        ).collect(Collectors.joining(",\n", "{\n  \"model\": \"" + model + "\",\n  \"results\": [\n", "\n  ]\n}\n"));
    }
}
