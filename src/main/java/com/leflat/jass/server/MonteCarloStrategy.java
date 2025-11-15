package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.stream.Collectors;

public class MonteCarloStrategy implements IJassStrategy {
    private final int strength;
    private final Random rand = new Random();
    private static final int OUR_TEAM_ID = 0;

    public MonteCarloStrategy(int strength) {
        this.strength = strength;
    }

    @Override
    public Card chooseCard(List<Card> validCards,
                           GameView gameView,
                           Plie currentPlie,
                           List<Card> hand,
                           int pliesWonByTeam) {
        Card bestCard = null;
        float bestScore = -1000;

        for (Card validCard : validCards) {
            var score = evaluateMoveRewardParallel(
                    gameView, currentPlie, hand, validCard, pliesWonByTeam, strength * 10
            );
            if (score > bestScore) {
                bestScore = score;
                bestCard = validCard;
            }
        }
        return bestCard;
    }

    @Override
    public int chooseTrumpSuit(boolean first, List<Card> hand, GameView gameView) {
        int bestAtout = Card.COLOR_NONE;
        float bestScore = -10000;

        // This is the logic from ArtificialPlayer.chooseBestAtout()
        for (int atout = Card.COLOR_SPADE; atout <= Card.COLOR_DIAMOND; atout++) {
            Card.atout = atout;
            var announcements = Announcement.findAnouncements(hand);
            float startScore = announcements.stream().mapToInt(Announcement::getValue).sum();
            if (atout == Card.COLOR_SPADE) {
                startScore *= 2;
            }
            float maxScore = -10000;
            // Create a dummy empty plie for the simulation
            Plie emptyPlie = new Plie();
            for (var card : hand) {
                // Use the parallel version for the more expensive atout choice
                float score = evaluateMoveRewardParallel(
                        gameView, emptyPlie, hand, card, 0, 5 * strength
                );
                if (score > maxScore) {
                    maxScore = score;
                }
            }
            if (startScore + maxScore > bestScore) {
                bestScore = startScore + maxScore;
                bestAtout = atout;
            }
        }
        return bestScore < 0 && first ? Card.COLOR_NONE : bestAtout;
    }

    // --- All private MC logic is now contained within this class ---

    private float evaluateMoveReward(GameView gameView, Plie currentPlie, List<Card> hand,
                                     Card move, int numberOfPliesWonByOwnTeam, int numberOfGames) {
        List<Card>[] hands = new List[4];
        int reward = 0;
        for (int game = 0; game < numberOfGames; game++) {
            int pliesCollected = numberOfPliesWonByOwnTeam;
            hands[0] = new ArrayList<>(hand);
            int i = 1;
            for (var h : gameView.getRandomHands()) {
                hands[i++] = h;
            }
            var plie = new Plie(currentPlie);
            PlayerPosition startPosition = PlayerPosition.fromCode((4 - plie.getSize()) % 4);
            try {
                plie.playCard(move, gameView.getPlayer(PlayerPosition.SELF), hands[0]);
            } catch (BrokenRuleException e) {
                e.printStackTrace();
            }
            hands[0].remove(move);
            int gameScore = 0;
            boolean weWonLastPlie;
            do {
                while (plie.getSize() < 4) {
                    var currentPosition = startPosition.next(plie.getSize());
                    final var finalPlie = new Plie(plie);
                    var validMoves = hands[currentPosition.getCode()].stream().filter(c -> finalPlie.canPlay(c, hands[currentPosition.getCode()])).collect(Collectors.toList());
                    if (validMoves.isEmpty()) {
                        throw new RuntimeException("No valid move!");
                    }
                    Card randomMove;
                    if (validMoves.size() == 1) {
                        randomMove = validMoves.get(0);
                    } else {
                        randomMove = validMoves.get(rand.nextInt(validMoves.size()));
                    }
                    try {
                        plie.playCard(randomMove, gameView.getPlayer(currentPosition), hands[currentPosition.getCode()]);
                    } catch (BrokenRuleException e) {
                        e.printStackTrace();
                    }
                    hands[currentPosition.getCode()].remove(randomMove);
                }
                weWonLastPlie = plie.getOwner().getTeam().getId() == OUR_TEAM_ID;
                if (weWonLastPlie) {
                    gameScore += plie.getScore();
                    pliesCollected++;
                } else {
                    gameScore -= plie.getScore();
                }
                plie = new Plie();
            } while (!hands[0].isEmpty());
            int cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
            gameScore += weWonLastPlie ? cinqDeDer : 0;
            int match = Card.atout == Card.COLOR_SPADE ? 200 : 100;
            if (pliesCollected == 9) {
                gameScore += match;
            } else if (pliesCollected == 0) {
                gameScore -= match;
            }
            reward += gameScore;
        }
        return (float) reward / numberOfGames;
    }

    // (evaluateMoveRewardParallel is identical, just paste it in here)
    // ...
    private float evaluateMoveRewardParallel(GameView gameView, Plie currentPlie, List<Card> hand,
                                             Card move, int numberOfPliesWonByOwnTeam, int numberOfGames) {
        if (numberOfGames <= 0) return 0.0f;

        // Thread pool: Use available processors
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        DoubleAccumulator totalReward = new DoubleAccumulator((a, b) -> a + b, 0.0);
        AtomicInteger completed = new AtomicInteger(0);

        // (Copied from ArtificialPlayer)
        int batchSize = Math.max(1, numberOfGames / numThreads * 2);
        for (int start = 0; start < numberOfGames; start += batchSize) {
            final int batchStart = start;
            final int batchEnd = Math.min(start + batchSize, numberOfGames);
            executor.submit(() -> {
                int pliesCollected = numberOfPliesWonByOwnTeam;
                List<Card>[] hands = new List[4];
                int localReward = 0;
                ThreadLocalRandom localRand = ThreadLocalRandom.current();  // Thread-safe random

                for (int game = batchStart; game < batchEnd; game++) {
                    hands[0] = new ArrayList<>(hand);
                    int i = 1;
                    for (var h : gameView.getRandomHands()) {
                        hands[i++] = h;
                    }
                    var plie = new Plie(currentPlie);
                    PlayerPosition startPosition = PlayerPosition.fromCode((4 - plie.getSize()) % 4);
                    try {
                        plie.playCard(move, gameView.getPlayer(PlayerPosition.SELF), hands[0]); // Use 'self'
                    } catch (BrokenRuleException e) {
                        e.printStackTrace();
                        continue;  // Skip bad sim
                    }
                    hands[0].remove(move);
                    int gameScore = 0;
                    boolean weWonLastPlie;
                    do {
                        while (plie.getSize() < 4) {
                            var currentPosition = startPosition.next(plie.getSize());
                            final var finalPlie = new Plie(plie);
                            var validMoves = hands[currentPosition.getCode()].stream()
                                    .filter(c -> finalPlie.canPlay(c, hands[currentPosition.getCode()]))
                                    .collect(Collectors.toList());
                            if (validMoves.isEmpty()) {
                                throw new RuntimeException("No valid move!");
                            }
                            Card randomMove;
                            if (validMoves.size() == 1) {
                                randomMove = validMoves.get(0);
                            } else {
                                randomMove = validMoves.get(localRand.nextInt(validMoves.size()));
                            }
                            try {
                                // Use 'self' and 'playersByPosition'
                                plie.playCard(randomMove, gameView.getPlayer(currentPosition), hands[currentPosition.getCode()]);
                            } catch (BrokenRuleException e) {
                                e.printStackTrace();
                                break;  // Skip bad trick
                            }
                            hands[currentPosition.getCode()].remove(randomMove);
                        }
                        weWonLastPlie = plie.getOwner().getTeam().getId() == OUR_TEAM_ID;
                        if (weWonLastPlie) {
                            gameScore += plie.getScore();
                            pliesCollected++;
                        } else {
                            gameScore -= plie.getScore();
                        }
                        plie = new Plie();
                    } while (!hands[0].isEmpty());
                    int cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
                    gameScore += weWonLastPlie ? cinqDeDer : 0;
                    int match = Card.atout == Card.COLOR_SPADE ? 200 : 100;
                    if (pliesCollected == 9) {
                        gameScore += match;
                    } else if (pliesCollected == 0) {
                        gameScore -= match;
                    }
                    localReward += gameScore;
                }
                totalReward.accumulate(localReward);
                completed.incrementAndGet();
            });
        }

        // ... (rest of method) ...
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ArtificialPlayer.LOGGER.warning("MC sim interrupted");
        }
        return (float) totalReward.get() / numberOfGames;
    }
}