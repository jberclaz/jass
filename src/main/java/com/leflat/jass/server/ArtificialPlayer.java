package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtificialPlayer extends AbstractRemotePlayer {
    private final static Logger LOGGER = Logger.getLogger(ArtificialPlayer.class.getName());
    private final List<Integer> drawnCards = new ArrayList<>();
    private final GameView gameView = new GameView();
    private final Map<Integer, Integer> playersPositions = new HashMap<>();
    private Plie currentPlie;
    private Card playedCard;
    private boolean hasStoeck;
    private final Random rand = new Random();
    private int ourScore = 0;
    private int theirScore = 0;

    public ArtificialPlayer(int id, String name) {
        super(id);
        setName(name);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        var relativePosition = getInitialRelativePosition(player);
        playersPositions.put(player.getId(), relativePosition);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        throw new RuntimeException("Artificial player should not have to choose team selection method");
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        drawnCards.clear();
    }

    @Override
    public int drawCard() {
        int randomCard;
        do {
            randomCard = rand.nextInt(36);
        } while (drawnCards.contains(randomCard));
        return randomCard;
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        drawnCards.add(cardPosition);
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) {
        int ownPosition = playerIds.indexOf(id);
        playersPositions.clear();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            playersPositions.put(playerId, (i - ownPosition + 4) % 4);
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
        System.out.println(name + " : " + hand);
    }

    @Override
    public int chooseAtout(boolean first) {
        int[] colors = {0, 0, 0, 0};
        for (var c : hand) {
            colors[c.getColor()]++;
        }
        int nbrColors = 0;
        int bestColor = -1;
        int bestColorCount = 0;
        for (int colorIdx = 0; colorIdx < 4; colorIdx++) {
            if (colors[colorIdx] > 0) {
                nbrColors++;
                if (colors[colorIdx] > bestColorCount) {
                    bestColor = colorIdx;
                    bestColorCount = colors[colorIdx];
                }
            }
        }
        if (first && nbrColors == 4 && bestColorCount < 4) {
            return Card.COLOR_NONE;
        }
        return bestColor;
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
        playedCard = chooseBestCard();
        try {
            currentPlie.playCard(playedCard, this, hand);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        removeCard(playedCard);
        return playedCard;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        var position = playersPositions.get(player.getId()) - 1;
        // if player doesn't follow, we know he doesn't have this color
        if (currentPlie.getSize() > 0 && card.getColor() != Card.atout && card.getColor() != currentPlie.getColor()) {
            var bourg = new Card(Card.RANK_BOURG, Card.atout);
            for (int r = 0; r < 9; r++) {
                var missingGard = new Card(r, currentPlie.getColor());
                if (!missingGard.equals(bourg)) {
                    gameView.playerDoesNotHaveCard(position, missingGard);
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
                System.out.println(name + " has " + announcements.size() + " announcements");
            }
            for (var a : announcements) {
                System.out.println(name + " announces " + a);
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
        var position = playersPositions.get(player.getId()) - 1;
        for (var announcement : announcements) {
            if (announcement.getType() == Announcement.STOECK) {
                // currently we can only announce stoeck when we play the last card
                continue;
            }
            for (var card : announcement.getCards()) {
                gameView.playerHasCard(position, card);
            }
            if (announcement.getType() == Announcement.THREE_CARDS || announcement.getType() == Announcement.FIFTY) {
                if (announcement.getCard().getRank() < Card.RANK_AS) {
                    int nextSuiteCard = announcement.getCard().getNumber() + 1;
                    if (!hand.contains(new Card(nextSuiteCard))) {
                        gameView.playerDoesNotHaveCard(position, nextSuiteCard);
                    }
                }
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
            var score = playRandomGames(hand, validCard, 100);
            if (score > bestScore) {
                bestScore = score;
                bestCard = validCard;
            }
        }
        return bestCard;
    }

    private float playRandomGames(List<Card> hand, Card card, int numberOfGames) {
        // var hands = gameView.getRandomHands();
        return rand.nextFloat() * 100;
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
}
