package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtificialPlayer extends AbstractRemotePlayer {
    protected final static Logger LOGGER = Logger.getLogger(ArtificialPlayer.class.getName());
    private final List<Integer> remainingCardsToDraw = new ArrayList<>();
    private final GameView gameView = new GameView();
    protected final Map<Integer, Integer> positionsByIds = new HashMap<>();
    private final Map<Integer, BasePlayer> playersByIds = new HashMap<>();
    private final Map<Integer, BasePlayer> playersByPosition = new HashMap<>();
    private Plie currentPlie;
    private Card playedCard;
    private boolean hasStoeck;
    private final Random rand = new Random();
    private int ourScore = 0;
    private int theirScore = 0;
    private int numberOfPliesWonByOwnTeam;
    private int strength = 1000;
    private boolean noWait = false;

    public ArtificialPlayer(int id, String name) {
        super(id);
        setName(name);
    }

    public ArtificialPlayer(int id, String name, int strength) {
        this(id, name);
        this.strength = strength;
    }

    public ArtificialPlayer(int id, String name, int strength, boolean noWait) {
        this(id, name, strength);
        this.noWait = noWait;
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
            int position = (i - ownPosition + 4) % 4;
            positionsByIds.put(playerId, position);
            if (position > 0) {
                playersByPosition.put(position, playersByIds.get(playerId));
            }
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
        gameView.reset(cards);
        currentPlie = new Plie();
        numberOfPliesWonByOwnTeam = 0;
    }

    @Override
    public int chooseAtout(boolean first) {
        return chooseBestAtout(first);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {
        if (color == Card.COLOR_NONE) {
            return;
        }
        // TODO: change opponent card probabilities
        announcements = Announcement.findAnouncements(hand);
        hasStoeck = Announcement.findStoeck(hand);
    }

    @Override
    public Card play() {
        long startTime = System.currentTimeMillis();
        playedCard = chooseBestCard();
        try {
            currentPlie.playCard(playedCard, this, hand);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        removeCard(playedCard);
        long endTime = System.currentTimeMillis();
        float elapsedTime = (endTime - startTime) / 1000f;
        if (elapsedTime < 1) {
            waitSec(1 - elapsedTime);
        }
        return playedCard;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        var position = positionsByIds.get(player.getId()) - 1;
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
        if (positionsByIds.get(player.getId()) % 2 == 0) {
            numberOfPliesWonByOwnTeam++;
        }
        currentPlie = new Plie();
    }

    @Override
    public void setScores(int score, int opponentScore) {
        ourScore = score;
        theirScore = opponentScore;
    }

    @Override
    public List<Announcement> getAnnouncements() {
        if (hand.size() == 8) {
            // can announce only on first plie
            if (!announcements.isEmpty()) {
                LOGGER.info(name + " has " + announcements.size() + " announcements");
            }
            for (var a : announcements) {
                LOGGER.info(name + " announces " + a);
            }
            return announcements;
        }
        if (playedStoeck()) {
            return Collections.singletonList(Announcement.getStoeck());
        }
        return Collections.emptyList();
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) {
        if (player.getId() == this.id) {
            return;
        }
        var position = positionsByIds.get(player.getId()) - 1;
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

    private int getInitialRelativePosition(BasePlayer player) {
        return (player.getId() - id + 4) % 4;
    }

    private Card chooseBestCard() {
        List<Card> validCards;
        if (currentPlie.getSize() == 0) {
            validCards = new ArrayList<>(hand);
        } else {
            validCards = hand.stream().filter(c -> currentPlie.canPlay(c, hand)).collect(Collectors.toList());
        }
        if (validCards.size() == 1) {
            return validCards.get(0);
        }
        Card bestCard = null;
        float bestScore = -1000;
        for (Card validCard : validCards) {
            var score = evaluateMoveReward(hand, validCard, strength * 10);
            if (score > bestScore) {
                bestScore = score;
                bestCard = validCard;
            }
        }
        LOGGER.info(name + " : best move is " + bestCard);
        return bestCard;
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
            int startPlayer = (4 - plie.getSize()) % 4;
            try {
                plie.playCard(move, this, hands[0]);
            } catch (BrokenRuleException e) {
                e.printStackTrace();
            }
            hands[0].remove(move);
            int gameScore = 0;
            int plieWinnerPosition;
            do {
                while (plie.getSize() < 4) {
                    int currentPlayer = (startPlayer + plie.getSize()) % 4;
                    final var finalPlie = new Plie(plie);
                    var validMoves = hands[currentPlayer].stream().filter(c -> finalPlie.canPlay(c, hands[currentPlayer])).collect(Collectors.toList());
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
                        plie.playCard(randomMove, currentPlayer == 0 ? this : playersByPosition.get(currentPlayer), hands[currentPlayer]);
                    } catch (BrokenRuleException e) {
                        e.printStackTrace();
                    }
                    hands[currentPlayer].remove(randomMove);
                }
                plieWinnerPosition = positionsByIds.get(plie.getOwner().getId());
                if (plieWinnerPosition % 2 == 0) {
                    gameScore += plie.getScore();
                    pliesCollected++;
                } else {
                    gameScore -= plie.getScore();
                }
                plie = new Plie();
            } while (!hands[0].isEmpty());
            int cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
            gameScore += plieWinnerPosition % 2 == 0 ? cinqDeDer : -cinqDeDer;
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
                float score = evaluateMoveReward(hand, card, 5 * strength);
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
}
