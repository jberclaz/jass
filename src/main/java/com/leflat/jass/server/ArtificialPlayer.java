package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtificialPlayer extends AbstractRemotePlayer implements AutoCloseable {
    protected final static Logger LOGGER = Logger.getLogger(ArtificialPlayer.class.getName());
    private final List<Integer> remainingCardsToDraw = new ArrayList<>();
    private final GameView gameView = new GameView();
    protected final Map<Integer, PlayerPosition> positionsByIds = new HashMap<>();
    private final Map<Integer, BasePlayer> playersByIds = new HashMap<>();
    private final Map<PlayerPosition, BasePlayer> playersByPosition = new HashMap<>();
    private Plie currentPlie;
    private Card playedCard;
    private boolean hasStoeck;
    private final Random rand = new Random();
    private int numberOfPliesWonByOwnTeam;
    private int strength = 1000;
    private boolean noWait = false;
    private DataOutputStream tokensDos = null;
    private final JassModelLoader modelLoader;

    public ArtificialPlayer(int id, String name) {
        super(id);
        setName(name);
        var modelPath = ArtificialPlayer.class.getClassLoader().getResource("model/jassformer.onnx");
        modelLoader = new JassModelLoader(modelPath.getPath());
    }

    public ArtificialPlayer(int id, String name, int strength) {
        this(id, name);
        this.strength = strength;
    }

    public ArtificialPlayer(int id, String name, int strength, boolean noWait) {
        this(id, name, strength);
        this.noWait = noWait;
    }

    public void extractTransformersTokens(String filename) throws FileNotFoundException {
        this.tokensDos = new DataOutputStream(new FileOutputStream(filename, false));
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        var relativePosition = getInitialRelativePosition(player);
        positionsByIds.put(player.getId(), relativePosition);
        playersByIds.put(player.getId(), player);
        playersByPosition.put(relativePosition, player);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        throw new RuntimeException("Artificial player should not have to choose team selection method");
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        remainingCardsToDraw.clear();
        for (int i=0; i<Card.DECK_SIZE; i++) {
            remainingCardsToDraw.add(i);
        }
    }

    @Override
    public int drawCard() {
        waitSec(0.5f);
        int randomPosition = rand.nextInt(remainingCardsToDraw.size());
        return remainingCardsToDraw.get(randomPosition);
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        remainingCardsToDraw.remove(Integer.valueOf(cardPosition));
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) {
        int ownPosition = playerIds.indexOf(id);
        positionsByIds.clear();
        playersByPosition.clear();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            var position = PlayerPosition.fromCode((i - ownPosition + 4) % 4);
            positionsByIds.put(playerId, position);
            playersByPosition.put(position, playersByIds.get(playerId));
        }
    }

    @Override
    public int choosePartner() {
        throw new RuntimeException("Artificial player should not have to choose partner");
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        Card.sort(cards);
        super.setHand(cards);
        gameView.reset(cards, positionsByIds);
        currentPlie = new Plie();
        numberOfPliesWonByOwnTeam = 0;
    }

    @Override
    public int chooseAtout(boolean first) {
        return chooseBestAtout(first);
    }

    @Override
    public void setTrump(int color, BasePlayer firstToPlay, boolean chosenOnFirstTurn) {
        if (color == Card.COLOR_NONE) {
            return;
        }
        // TODO: change opponent card probabilities
        var position = positionsByIds.get(firstToPlay.getId());
        gameView.setTrump(chosenOnFirstTurn ? position : position.opposite(), chosenOnFirstTurn);
        announcements = Announcement.findAnouncements(hand);
        hasStoeck = Announcement.findStoeck(hand);
    }

    @Override
    public Card play() {
        long startTime = System.currentTimeMillis();
        byte[] tokens = new byte[0];
        if (tokensDos != null) {
            tokens = gameView.encodeStateForTransformer();
        }
        playedCard = chooseBestCard();
        try {
            currentPlie.playCard(playedCard, this, hand);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        removeCard(playedCard);
        if (tokensDos != null) {
            try {
                tokensDos.write(tokens);
                tokensDos.write((byte)playedCard.getNumber());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        long endTime = System.currentTimeMillis();
        float elapsedTime = (endTime - startTime) / 1000f;
        if (elapsedTime < 1) {
            waitSec(1 - elapsedTime);
        }
        gameView.cardPlayed(PlayerPosition.SELF, playedCard);
        return playedCard;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        var position = positionsByIds.get(player.getId());
        // if player doesn't follow, we know he doesn't have this color
        if (currentPlie.getSize() > 0 && card.getColor() != Card.atout && card.getColor() != currentPlie.getColor()) {
            var bourg = new Card(Card.RANK_BOURG, Card.atout);
            for (int r = 0; r < 9; r++) {
                var missingCard = new Card(r, currentPlie.getColor());
                if (!missingCard.equals(bourg)) {
                    gameView.playerDoesNotHaveCard(position, missingCard);
                }
            }
        }
        try {
            currentPlie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            LOGGER.log(Level.SEVERE, "Error: broken rule", e);
            System.exit(1);
        }
        gameView.cardPlayed(position, card);
    }

    @Override
    public void collectPlie(BasePlayer player) {
        if (positionsByIds.get(player.getId()).ourTeam()) {
            numberOfPliesWonByOwnTeam++;
        }
        gameView.setCompletedTrick(currentPlie);
        currentPlie = new Plie();
    }

    @Override
    public void setScores(int score, int opponentScore) {
        gameView.updateMatchScore(score, opponentScore);
    }

    @Override
    public List<Announcement> getAnnouncements() {
        if (hand.size() == 8) {
            // can announce only on first plie
//            if (!announcements.isEmpty()) {
//                LOGGER.info(name + " has " + announcements.size() + " announcements");
//            }
//            for (var a : announcements) {
//                LOGGER.info(name + " announces " + a);
//            }
            return announcements;
        }
        if (playedStoeck()) {
            return Collections.singletonList(Announcement.getStoeck());
        }
        return Collections.emptyList();
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) {
        var ourTeam = positionsByIds.get(player.getId()).ourTeam();
        for (var announcement : announcements) {
            gameView.addAnnouncementScore(announcement.getValue(), ourTeam);
        }
        if (player.getId() == this.id) {
            return;
        }
        var position = positionsByIds.get(player.getId());
        for (var announcement : announcements) {
            if (announcement.getType() == Announcement.STOECK) {
                // currently we can only announce stoeck when we play the last card => by then, cards have already been played
                continue;
            }
            for (var card : announcement.getCards()) {
                gameView.playerHasCard(position, card);
            }
            if (announcement.getType() == Announcement.THREE_CARDS || announcement.getType() == Announcement.FIFTY || announcement.getType() == Announcement.HUNDRED) {
                if (announcement.getCard().getRank() < Card.RANK_AS) {
                    int nextSuiteCard = announcement.getCard().getNumber() + 1;
                    if (!hand.contains(new Card(nextSuiteCard))) {
                        gameView.playerDoesNotHaveCard(position, nextSuiteCard);
                    }
                }
            }
            if (announcement.getType() == Announcement.THREE_CARDS || announcement.getType() == Announcement.FIFTY) {
                int previousSuiteCardRank = announcement.getCard().getRank();
                previousSuiteCardRank -= announcement.getType() == Announcement.THREE_CARDS ? 3 : 4;
                if (previousSuiteCardRank >= 0) {
                    var previousSuiteCard = new Card(previousSuiteCardRank, announcement.getCard().getColor());
                    if (!hand.contains(previousSuiteCard)) {
                        gameView.playerDoesNotHaveCard(position, previousSuiteCard);
                    }
                }
            }
        }
    }

    @Override
    public void setGameResult(Team winningTeam) {

    }

    @Override
    public boolean getNewGame() {
        throw new RuntimeException("Artificial player should not have to choose new game");
    }

    @Override
    public void playerLeft(BasePlayer player) {

    }

    @Override
    public void lostServerConnection() {

    }

    @Override
    public void setHandScore(int ourScore, int theirScore, Team match) {

    }

    private PlayerPosition getInitialRelativePosition(BasePlayer player) {
        return PlayerPosition.fromCode((player.getId() - id + 4) % 4);
    }

    private Card chooseBestCardNn(List<Card> validCards) {

        if (modelLoader == null) {
            // Fallback to MC
            return chooseBestCardMc(validCards);
        }

        // Transformer inference
        byte[] tokens = gameView.encodeStateForTransformer();  // Your 95-byte array
        float[] logits = modelLoader.predict(tokens);
        Card bestCard = modelLoader.chooseBestCard(logits, validCards);

        LOGGER.info(name + " : Transformer chose " + bestCard);
        return bestCard;
    }

    // Rename old method as fallback
    private Card chooseBestCardMc(List<Card> validCards) {
        Card bestCard = null;
        float bestScore = -1000;
        for (Card validCard : validCards) {
            var score = evaluateMoveReward(hand, validCard, strength * 10);
            if (score > bestScore) {
                bestScore = score;
                bestCard = validCard;
            }
        }
        LOGGER.info(name + " : MC fallback chose " + bestCard);
        return bestCard;
    }

    private Card chooseBestCard() {
        List<Card> validCards;
        if (currentPlie.getSize() == 0) {
            validCards = new ArrayList<>(hand);
        } else {
            validCards = hand.stream().filter(c -> currentPlie.canPlay(c, hand)).collect(Collectors.toList());
        }
        if (validCards.size() == 1) {
            return validCards.getFirst();
        }
        return chooseBestCardNn(validCards);
    }

    private float evaluateMoveRewardParallel(List<Card> hand, Card move, int numberOfGames) {
        if (numberOfGames <= 0) return 0.0f;

        // Thread pool: Use available processors
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        DoubleAccumulator totalReward = new DoubleAccumulator((a, b) -> a + b, 0.0);
        AtomicInteger completed = new AtomicInteger(0);

        // Batch size: Submit in groups to reduce overhead
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
                        plie.playCard(move, this, hands[0]);
                    } catch (BrokenRuleException e) {
                        e.printStackTrace();
                        continue;  // Skip bad sim
                    }
                    hands[0].remove(move);
                    int gameScore = 0;
                    PlayerPosition plieWinnerPosition;
                    do {
                        while (plie.getSize() < 4) {
                            var currentPosition = startPosition.add(plie.getSize());
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
                                plie.playCard(randomMove, currentPosition == PlayerPosition.SELF ? this : playersByPosition.get(currentPosition), hands[currentPosition.getCode()]);
                            } catch (BrokenRuleException e) {
                                e.printStackTrace();
                                break;  // Skip bad trick
                            }
                            hands[currentPosition.getCode()].remove(randomMove);
                        }
                        plieWinnerPosition = positionsByIds.get(plie.getOwner().getId());
                        if (plieWinnerPosition.ourTeam()) {
                            gameScore += plie.getScore();
                            pliesCollected++;
                        } else {
                            gameScore -= plie.getScore();
                        }
                        plie = new Plie();
                    } while (!hands[0].isEmpty());
                    int cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
                    gameScore += plieWinnerPosition.ourTeam() ? cinqDeDer : 0;
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

        // Wait for completion
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);  // Adjust timeout if needed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("MC sim interrupted");
        }

        return (float) totalReward.get() / numberOfGames;
    }

    private float evaluateMoveReward(List<Card> hand, Card move, int numberOfGames) {
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
                plie.playCard(move, this, hands[0]);
            } catch (BrokenRuleException e) {
                e.printStackTrace();
            }
            hands[0].remove(move);
            int gameScore = 0;
            PlayerPosition plieWinnerPosition;
            do {
                while (plie.getSize() < 4) {
                    var currentPosition = startPosition.add(plie.getSize());
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
                        plie.playCard(randomMove, currentPosition == PlayerPosition.SELF ? this : playersByPosition.get(currentPosition), hands[currentPosition.getCode()]);
                    } catch (BrokenRuleException e) {
                        e.printStackTrace();
                    }
                    hands[currentPosition.getCode()].remove(randomMove);
                }
                plieWinnerPosition = positionsByIds.get(plie.getOwner().getId());
                if (plieWinnerPosition.ourTeam()) {
                    gameScore += plie.getScore();
                    pliesCollected++;
                } else {
                    gameScore -= plie.getScore();
                }
                plie = new Plie();
            } while (!hands[0].isEmpty());
            int cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
            gameScore += plieWinnerPosition.ourTeam() ? cinqDeDer : 0;
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

    private boolean playedStoeck() {
        if (!hasStoeck) {
            return false;
        }
        if (playedCard.getColor() != Card.atout ||
                (playedCard.getRank() != Card.RANK_DAME &&
                        playedCard.getRank() != Card.RANK_ROI)) {
            return false;
        }
        for (var card : hand) {
            if (card.getColor() == Card.atout &&
                    (card.getRank() == Card.RANK_DAME ||
                            card.getRank() == Card.RANK_ROI)) {
                return false;
            }
        }
        return true;
    }

    int chooseBestAtout(boolean canPass) {
        int bestAtout = Card.COLOR_NONE;
        float bestScore = -10000;
        for (int atout = Card.COLOR_SPADE; atout <= Card.COLOR_DIAMOND; atout++) {
            Card.atout = atout;
            var announcements = Announcement.findAnouncements(hand);
            float startScore = announcements.stream().mapToInt(Announcement::getValue).sum();
            if (atout == Card.COLOR_SPADE) {
                startScore *= 2;
            }
            float maxScore = -10000;
            for (var card : hand) {
                float score = evaluateMoveRewardParallel(hand, card, 5 * strength);
                if (score > maxScore) {
                    maxScore = score;
                }
            }
            if (startScore + maxScore > bestScore) {
                bestScore = startScore + maxScore;
                bestAtout = atout;
            }
        }
        return bestScore < 0 && canPass ? Card.COLOR_NONE : bestAtout;
    }

    void waitSec(float seconds) {
        if (noWait) {
            return;
        }
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void close() throws Exception {
        if (tokensDos != null) {
            tokensDos.close();
        }
    }
}
