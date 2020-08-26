package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtificialPlayer extends AbstractRemotePlayer {
    private final static Logger LOGGER = Logger.getLogger(ArtificialPlayer.class.getName());
    private List<Integer> drawnCards = new ArrayList<>();
    private Float[][] cardsInGame = new Float[3][36];
    private final Map<Integer, Integer> playersPositions = new HashMap<>();
    private final Map<Integer, PlayerView> players = new HashMap<>();
    private Plie currentPlie;

    public ArtificialPlayer(int id, String name) {
        super(id);
        setName(name);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        var relativePosition = getInitialRelativePosition(player);
        playersPositions.put(player.getId(), relativePosition);
        players.put(player.getId(), new PlayerView(player.getId()));
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        //throw new RuntimeException("Artificial player should not have to choose team selection method");
        return TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        drawnCards.clear();
    }

    @Override
    public int drawCard() {
        // TODO: implement
        return 0;
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        drawnCards.add(card.getNumber());
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
        //throw new RuntimeException("Artificial player should not have to choose partner");
        return new Random().nextInt(3) + 1;
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        super.setHand(cards);
        players.values().forEach(PlayerView::reset);
        resetCardProbs();
        currentPlie = new Plie();
    }

    @Override
    public int chooseAtout(boolean first) {
        // TODO: implement
        return Card.COLOR_SPADE;
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {

    }

    @Override
    public Card play() {
        // TODO: implement
        var cardPlayed = chooseBestCard();
        try {
            currentPlie.playCard(cardPlayed, this, hand);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        removeCard(cardPlayed);
        return cardPlayed;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        // TODO: update cardsInGame if the player is not following
        try {
            currentPlie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            LOGGER.log(Level.SEVERE, "Error: broken rule", e);
            System.exit(1);
        }
        for (int i = 0; i < 3; i++) {
            cardsInGame[i][card.getNumber()] = 0f;
        }
    }

    @Override
    public void collectPlie(BasePlayer player) {
        currentPlie = new Plie();
    }

    @Override
    public void setScores(int score, int opponentScore) {

    }

    @Override
    public List<Announcement> getAnnouncements() throws PlayerLeftExpection {
        return null;
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) {
        var position = playersPositions.get(player.getId()) - 1;
        for (var announcement : announcements) {
            if (announcement.getType() == Announcement.STOECK) {
                // currently we can only announce stoeck when we play the last card
                continue;
            }
            for (var card : announcement.getCards()) {
                for (int p =0; p < 3; p++) {
                    cardsInGame[p][card.getNumber()] = p == position ? 1f : 0f;
                }
            }
            if (announcement.getType() == Announcement.THREE_CARDS || announcement.getType() == Announcement.FIFTY) {
                if (announcement.getCard().getRank() < Card.RANK_AS) {
                    int nextSuiteCard = announcement.getCard().getNumber()+1;
                    if (!hand.contains(new Card(nextSuiteCard))) {
                        for (int p = 0; p < 3; p++) {
                            cardsInGame[p][nextSuiteCard] = p == position ? 0f : .5f;
                        }
                    }
                }
                int previousSuiteCardRank = announcement.getCard().getRank();
                previousSuiteCardRank -= announcement.getType() == Announcement.THREE_CARDS ? 3 :4;
                if (previousSuiteCardRank >= 0) {
                    var previousSuiteCard = new Card(previousSuiteCardRank, announcement.getCard().getColor());
                    if (!hand.contains(previousSuiteCard)) {
                        for (int p = 0; p < 3; p++) {
                            cardsInGame[p][previousSuiteCard.getNumber()] = p == position ? 0f : .5f;
                        }
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
        return false;
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

    private void resetCardProbs() {
        for (int i = 0; i < 36; i++) {
            var card = new Card(i);
            float prob = hand.contains(card) ? 0f : 1 / 3f;
            for (int p = 0; p < 3; p++) {
                cardsInGame[p][i] = prob;
            }
        }
    }

    private int getInitialRelativePosition(BasePlayer player) {
        return (player.getId() - id + 4) % 4;
    }

    private Card chooseBestCard() {
        List<Card> validCards;
        if (currentPlie.getSize() == 0) {
            validCards = new ArrayList<>(hand);
        }
        else {
            validCards = hand.stream().filter(c->currentPlie.canPlay(c, hand)).collect(Collectors.toList());
        }
        Card bestCard = null;
        float bestScore = -1000;
        for (int ci=0; ci<validCards.size(); ci++) {
            var score = playRandomGames(hand, cardsInGame, validCards.get(ci), 100);
            if (score > bestScore) {
                bestScore = score;
                bestCard = validCards.get(ci);
            }
        }
        return bestCard;
    }

    private float playRandomGames(List<Card> hand, Float[][] cardsInGame, Card card, int numberOfGames) {
        return 0;
    }
}
