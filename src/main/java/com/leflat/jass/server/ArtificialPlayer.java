package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
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
    private boolean noWait = false;
    private DataOutputStream tokensDos = null;
    private IJassStrategy playStrategy;

    public ArtificialPlayer(int id, String name) {
        this(id, name, new MonteCarloStrategy(1000));
    }

    public ArtificialPlayer(int id, String name, IJassStrategy strategy) {
        super(id);
        setName(name);
        this.playStrategy = strategy;
    }

    public ArtificialPlayer(int id, String name, IJassStrategy strategy, boolean noWait) {
        this(id, name, strategy);
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
        gameView.reset(cards);
        currentPlie = new Plie();
        numberOfPliesWonByOwnTeam = 0;
    }

    @Override
    public int chooseAtout(boolean first) {
        return playStrategy.chooseTrumpSuit(first, hand, gameView);
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
        return playStrategy.chooseCard(validCards, gameView, currentPlie, hand, numberOfPliesWonByOwnTeam);
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
