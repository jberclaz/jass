/*
 * Created for and by the FLAT(r)
 */
package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.AbstractRemotePlayer;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InteractivePlayer extends AbstractRemotePlayer {
    private final static Logger LOGGER = Logger.getLogger(InteractivePlayer.class.getName());
    private final IJassUi ui;
    private final Map<Integer, Integer> playersPositions = new HashMap<>();
    private final Map<Integer, BasePlayer> players = new HashMap<>();
    private Plie plie;
    private Card playedCard;
    private boolean hasStoeck;

    public InteractivePlayer(IJassUi ui, int playerId, String name, int gameId) {
        super(playerId);
        this.ui = ui;
        players.put(playerId, new ClientPlayer(playerId, name));
        playersPositions.put(playerId, 0);
        ui.setPlayer(new ClientPlayer(playerId, name), 0);
        this.name = name;
        if (gameId >= 0) {
            ui.setGameId(gameId);
        }
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        var relativePosition = getInitialRelativePosition(player);
        playersPositions.put(player.getId(), relativePosition);
        players.put(player.getId(), player);
        ui.setPlayer(player, relativePosition);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        return ui.chooseTeamSelectionMethod();
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        ui.prepareTeamDrawing();
        if (!firstAttempt) {
            ui.displayStatusMessage("Le tirage des équipes a échoué. On recommence.");
        }
    }

    @Override
    public int drawCard() {
        var lock = new ReentrantLock();
        lock.lock();
        var condition = lock.newCondition();
        ui.displayStatusMessage("Veuillez choisir une carte");
        ui.drawCard(lock, condition);
        try {
            condition.await();

        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Error while waiting for player", e);
        }
        lock.unlock();

        ui.displayStatusMessage("");
        return ui.getDrawnCardPosition();
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        var relativePosition = playersPositions.get(player.getId());
        if (relativePosition == null) {
            LOGGER.severe("Error: unknown player " + player.getId());
            return;
        }
        ui.setDrawnCard(relativePosition, cardPosition, card);
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) {
        assert playerIds.size() == 4;
        int ownPosition = playerIds.indexOf(id);
        playersPositions.clear();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            playersPositions.put(playerId, (i - ownPosition + 4) % 4);
        }
        for (var player : players.values()) {
            ui.setPlayer(player, playersPositions.get(player.getId()));
        }
    }

    @Override
    public int choosePartner() {
        var partners = players.values().stream()
                .filter(p -> p.getId() != id)
                .collect(Collectors.toList());
        return ui.choosePartner(partners).getId();
    }

    @Override
    public void setHand(List<Card> cards) {
        Card.sort(cards);
        try {
            super.setHand(cards);
        } catch (PlayerLeftExpection ignored) {
        }
        ui.prepareGame();
        ui.setPlayerHand(cards);
        ui.setOtherPlayersHands(9);
        plie = new Plie();
    }

    @Override
    public int chooseAtout(boolean first) {
        return ui.chooseAtout(first);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {
        Card.atout = color;
        ui.setAtout(color, playersPositions.get(firstToPlay.getId()));
        if (color != Card.COLOR_NONE) {
            announcements = Announcement.findAnouncements(hand);
            hasStoeck = Announcement.findStoeck(hand);
        }
    }

    @Override
    public Card play() {
        ui.displayStatusMessage("A vous de jouer...");
        ui.setAnnouncementEnabled(true);
        var lock = new ReentrantLock(); //= controller.getLock();
        lock.lock();
        var condition = lock.newCondition();

        do {
            ui.chooseCard(lock, condition);
            try {
                condition.await();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error while waiting for player", e);
            }
            var card = ui.getChosenCard();
            try {
                plie.playCard(card, this, hand);
                playedCard = card;
                break;
            } catch (BrokenRuleException e) {
                switch (e.getBrokenRule()) {
                    case Rules.RULES_MUST_FOLLOW:
                        ui.displayStatusMessage("Il faut suivre!");
                        break;
                    case Rules.RULES_CANNOT_UNDERCUT:
                        ui.displayStatusMessage("Vous ne pouvez pas sous-couper!");
                        break;
                    default:
                        LOGGER.severe("Unknown rule " + e.getBrokenRule());
                }
            }
        } while (true);

        lock.unlock();

        removeCard(playedCard);
        ui.setAnnouncementEnabled(false);
        ui.setPlayerHand(hand);
        ui.setPlayedCard(playedCard, 0);
        ui.displayStatusMessage("");
        return playedCard;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        try {
            plie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            LOGGER.log(Level.SEVERE, "Error: broken rule", e);
            System.exit(1);
        }
        int position = playersPositions.get(player.getId());
        ui.setPlayedCard(card, position);
        ui.removeCardFromPlayerHand(position);
    }

    @Override
    public void collectPlie(BasePlayer player) {
        int position = playersPositions.get(player.getId());
        ui.collectPlie(position);
        plie = new Plie();
    }

    @Override
    public void setScores(int score, int opponentScore) {
        ui.setScore(score, opponentScore);
    }

    @Override
    public List<Announcement> getAnnouncements() {
        if (!ui.hasPlayerAnnounced()) {
            return Collections.emptyList();
        }
        if (hand.size() == 8) {
            // can announce only on first plie
            return announcements;
        }
        if (playedStoeck()) {
            return Collections.singletonList(Announcement.getStoeck());
        }
        return Collections.emptyList();
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) {
        StringBuilder sb;
        if (player.getId() == id) {
            sb = new StringBuilder("Vous annoncez ");
        } else {
            sb = new StringBuilder(players.get(player.getId()).getName()).append(" annonce ");
        }
        sb.append(announcements.get(0));
        for (int i = 1; i < announcements.size(); i++) {
            sb.append(" et ").append(announcements.get(i));
        }
        ui.displayStatusMessage(sb.toString());
    }

    @Override
    public void setGameResult(Team winningTeam) {
        boolean won = winningTeam.getPlayer(0).getId() == id || winningTeam.getPlayer(1).getId() == id;
        if (!won) {
            var p0 = winningTeam.getPlayer(0);
            p0.setName(players.get(p0.getId()).getName());
            var p1 = winningTeam.getPlayer(1);
            p1.setName(players.get(p1.getId()).getName());
        }
        ui.displayGameResult(winningTeam, won);
    }

    @Override
    public boolean getNewGame() {
        return ui.getNewGame();
    }

    @Override
    public void playerLeft(BasePlayer player) {
        ui.canceledGame(playersPositions.get(player.getId()));
    }

    @Override
    public void lostServerConnection() {
        ui.lostServerConnection();
    }

    @Override
    public void setHandScore(int ourScore, int theirScore, Team match) {
        if (match != null) {
            boolean us = id == match.getPlayer(0).getId() || id == match.getPlayer(1).getId();
            if (!us) {
                var p0 = match.getPlayer(0);
                p0.setName(players.get(p0.getId()).getName());
                var p1 = match.getPlayer(1);
                p1.setName(players.get(p1.getId()).getName());
            }
            ui.displayMatch(match, us);
        } else {
            ui.displayStatusMessage(String.format("Résultat de la manche: nous %d, eux %d", ourScore, theirScore));
        }
    }

    private int getInitialRelativePosition(BasePlayer player) {
        return (player.getId() - id + 4) % 4;
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
