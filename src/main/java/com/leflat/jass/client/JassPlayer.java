/*
 * Created for and by the FLAT(r)
 */
package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.AbstractRemotePlayer;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.*;
import java.util.stream.Collectors;

public class JassPlayer extends AbstractRemotePlayer implements IRemotePlayer {
    private IJassUi frame;
    private Map<Integer, Integer> playersPositions = new HashMap<>();
    private Map<Integer, BasePlayer> players = new HashMap<>();
    private Plie plie;
    private IControllerFactory controllerFactory;
    private IController controller = null;
    private Thread controllerThread = null;

    public JassPlayer(IControllerFactory controllerFactory, IJassUiFactory uiFactory) {
        super(-1);
        this.controllerFactory = controllerFactory;
        frame = uiFactory.getUi(this);

        frame.showUi(true);
        // TODO: remove (DEBUG)
        //Random rnd = new Random();
        //connect(String.valueOf(rnd.nextInt(100)), "localhost", 0);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        try {
            var relativePosition = getInitialRelativePosition(player);
            playersPositions.put(player.getId(), relativePosition);
            players.put(player.getId(), player);
            frame.setPlayer(player, relativePosition);
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
                e.printStackTrace();
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
    }

    @Override
    public Card play() {
        frame.displayStatusMessage("A vous de jouer...");
        frame.setAnnouncementEnabled(true);
        Card card;
        var lock = controller.getLock();
        lock.lock();
        var condition = lock.newCondition();

        do {
            frame.chooseCard(lock, condition);
            try {
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            card = frame.getChosenCard();
            try {
                plie.playCard(card, this, hand);
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
                        System.err.println("Unknown rule " + e.getBrokenRule());
                }
            }
        } while (true);

        lock.unlock();

        removeCard(card);
        frame.setAnnouncementEnabled(false);
        frame.setPlayerHand(hand);
        frame.setPlayedCard(card, 0);
        frame.displayStatusMessage("");
        return card;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        try {
            plie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
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
    public List<Anouncement> getAnoucement() {

        // TODO: compute annoucement in Play method and store it somewhere. Here, it's too late and one card would already have been removed from the hand.
        if (!frame.hasPlayerAnnounced()) {
            return Collections.emptyList();
        }
        // TODO: handle stoeck properly (only communicated when playing the second card)
        return Anouncement.findAnouncements(hand);
    }

    @Override
    public void setAnouncement(BasePlayer player, List<Anouncement> anouncements) {
        StringBuilder sb;
        if (player.getId() == id) {
            sb = new StringBuilder("Vous annoncez ");
        } else {
            sb = new StringBuilder(players.get(player.getId()).getName()).append(" annonce ");
        }
        sb.append(anouncements.get(0));
        for (int i = 1; i < anouncements.size(); i++) {
            sb.append(" et ").append(anouncements.get(i));
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
        controller.disconnect();
    }

    @Override
    public int connect(String name, String host, int gameId) {
        controller = controllerFactory.getController(this);
        var connectionInfo = controller.connect(host, gameId, name);
        if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
            return connectionInfo.error;
        }
        id = connectionInfo.playerId;
        playersPositions.put(id, 0);
        controllerThread = new Thread(controller, "controller-thread");
        controllerThread.start();
        try {
            frame.setPlayer(new ClientPlayer(id, name), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectionInfo.gameId;
    }

    @Override
    public boolean disconnect() {
        controller.disconnect();
        try {
            controllerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        controller = null;
        controllerThread = null;
        return true;
    }

    @Override
    public boolean isConnected() {
        return controller != null && controller.isConnected();
    }

    private int getInitialRelativePosition(BasePlayer player) {
        return (player.getId() - id + 4) % 4;
    }
}
