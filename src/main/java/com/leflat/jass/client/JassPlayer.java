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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JassPlayer extends AbstractRemotePlayer implements IRemotePlayer {
    private final static Logger LOGGER = Logger.getLogger(JassPlayer.class.getName());
    private IJassUi frame;
    private Map<Integer, Integer> playersPositions = new HashMap<>();
    private Map<Integer, BasePlayer> players = new HashMap<>();
    private Plie plie;
    private Card playedCard;

    private IController controller = null;
    private Thread controllerThread = null;
    private IClientNetwork network = null;
    private IClientNetworkFactory networkFactory;
    private boolean hasStoeck;

    public JassPlayer(IClientNetworkFactory networkFactory, IJassUiFactory uiFactory) {
        super(-1);
        this.networkFactory = networkFactory;
        frame = uiFactory.getUi(this);

        frame.showUi(true);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        try {
            var relativePosition = getInitialRelativePosition(player);
            playersPositions.put(player.getId(), relativePosition);
            players.put(player.getId(), player);
            frame.setPlayer(player, relativePosition);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while receiving player info", e);
        }
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        return frame.chooseTeamSelectionMethod();
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        frame.prepareTeamDrawing();
        if (!firstAttempt) {
            frame.displayStatusMessage("Le tirage des équipes a échoué. On recommence.");
        }
    }

    @Override
    public int drawCard() {
        var lock = controller.getLock();
        lock.lock();
        var condition = lock.newCondition();
        frame.displayStatusMessage("Veuillez choisir une carte");
        frame.drawCard(lock, condition);
        try {
            condition.await();

        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Error while waiting for player", e);
        }
        lock.unlock();

        frame.displayStatusMessage("");
        return frame.getDrawnCardPosition();
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        try {
            var relativePosition = playersPositions.get(player.getId());
            frame.setDrawnCard(relativePosition, cardPosition, card);
        } catch (IndexOutOfBoundsException e) {
            LOGGER.log(Level.WARNING, "Unknown player", e);
        }
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) {
        int ownPosition = playerIds.indexOf(id);
        playersPositions.clear();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            playersPositions.put(playerId, (i - ownPosition + 4) % 4);
        }
        for (var player : players.values()) {
            try {
                frame.setPlayer(player, playersPositions.get(player.getId()));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error while reordering players", e);
            }
        }
    }

    @Override
    public int choosePartner() {
        var partners = players.values().stream().filter(p -> p.getId() != id).collect(Collectors.toList());
        return frame.choosePartner(partners).getId();
    }

    @Override
    public void setHand(List<Card> cards) {
        Card.sort(cards);
        try {
            super.setHand(cards);
        } catch (PlayerLeftExpection ignored) {
        }
        frame.prepareGame();
        frame.setPlayerHand(cards);
        frame.setOtherPlayersHands(9);
        plie = new Plie();
    }

    @Override
    public int chooseAtout(boolean first) {
        return frame.chooseAtout(first);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {
        Card.atout = color;
        frame.setAtout(color, playersPositions.get(firstToPlay.getId()));
        announcements = Announcement.findAnouncements(hand);
        hasStoeck = Announcement.findStoeck(hand);
    }

    @Override
    public Card play() {
        frame.displayStatusMessage("A vous de jouer...");
        frame.setAnnouncementEnabled(true);
        var lock = controller.getLock();
        lock.lock();
        var condition = lock.newCondition();

        do {
            frame.chooseCard(lock, condition);
            try {
                condition.await();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error while waiting for player", e);
            }
            var card = frame.getChosenCard();
            try {
                plie.playCard(card, this, hand);
                playedCard = card;
                break;
            } catch (BrokenRuleException e) {
                switch (e.getBrokenRule()) {
                    case Rules.RULES_MUST_FOLLOW:
                        frame.displayStatusMessage("Il faut suivre!");
                        break;
                    case Rules.RULES_CANNOT_UNDERCUT:
                        frame.displayStatusMessage("Vous ne pouvez pas sous-couper!");
                        break;
                    default:
                        LOGGER.severe("Unknown rule " + e.getBrokenRule());
                }
            }
        } while (true);

        lock.unlock();

        removeCard(playedCard);
        frame.setAnnouncementEnabled(false);
        frame.setPlayerHand(hand);
        frame.setPlayedCard(playedCard, 0);
        frame.displayStatusMessage("");
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
        frame.removeCardFromPlayerHand(position);
        frame.setPlayedCard(card, position);
    }

    @Override
    public void collectPlie(BasePlayer player) {
        int position = playersPositions.get(player.getId());
        frame.collectPlie(position);
        plie = new Plie();
    }

    @Override
    public void setScores(int score, int opponentScore) {
        frame.setScore(score, opponentScore);
    }

    @Override
    public List<Announcement> getAnnouncements() {
        if (!frame.hasPlayerAnnounced()) {
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
        frame.displayStatusMessage(sb.toString());
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
        frame.displayGameResult(winningTeam, won);
    }

    @Override
    public boolean getNewGame() {
        return frame.getNewGame();
    }

    @Override
    public void playerLeft(BasePlayer player) {
        frame.canceledGame(playersPositions.get(player.getId()));
        disconnect();
    }

    @Override
    public void lostServerConnection() {
        disconnect();
        frame.lostServerConnection();
    }

    @Override
    public void setMatch(Team team) {
        boolean ourMatch = id == team.getPlayer(0).getId() || id == team.getPlayer(1).getId();
        frame.displayMatch(team, ourMatch);
    }

    @Override
    public int connect(String name, String host, int gameId) {
        network = networkFactory.getClientNetwork();
        var connectionInfo = network.connect(host, gameId, name);
        if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
            network = null;
            return connectionInfo.error;
        }
        id = connectionInfo.playerId;
        playersPositions.put(id, 0);
        controller = new RemoteController(this, network);
        controllerThread = new Thread(controller, "controller-thread");
        controllerThread.start();
        try {
            frame.setPlayer(new ClientPlayer(id, name), 0);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while setting player info", e);
        }
        return connectionInfo.gameId;
    }

    @Override
    public boolean disconnect() {
        if (controller != null) {
            controller.terminate();
        }
        network.disconnect();
        if (controllerThread != null) {
            try {
                controllerThread.join(500);
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error while waiting for controller thread to die", e);
            }
        }
        controller = null;
        controllerThread = null;
        return true;
    }

    @Override
    public boolean isConnected() {
        return network != null && network.isConnected();
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
