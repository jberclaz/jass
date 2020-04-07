/*
 * Created for and by the FLAT(r)
 */
package com.leflat.jass.client;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.stream.Collectors;

public class JassPlayer implements IPlayer, IRemotePlayer {
    private RemoteController controller = null;
    private IJassUi frame;
    private int id;
    private Map<Integer, Integer> playersPositions = new HashMap<>();
    private Map<Integer, BasePlayer> players = new HashMap<>();
    private List<Card> playerHand = new ArrayList<>();


    public JassPlayer() {
        frame = new JassFrame(this);
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
        frame.prepareTeamDrawing(firstAttempt);
    }

    @Override
    public int drawCard() {
        synchronized (controller) {
            frame.drawCard(controller);
            try {
                controller.wait();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        playerHand.clear();
        Card.sort(cards);
        playerHand.addAll(cards);
        frame.prepareGame();
        frame.setPlayerHand(cards);
        frame.setOtherPlayersHands(9);
    }

    @Override
    public int chooseAtout(boolean first) {
        return frame.chooseAtout(first);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {
        frame.setAtout(color, playersPositions.get(firstToPlay.getId()));
    }

    @Override
    public Card play(int currentColor, int highestRank, boolean cut) {
        synchronized (controller) {
            frame.play(new Plie(currentColor, highestRank, cut), controller);
            try {
                controller.wait();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return frame.getPlayedCard();
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        frame.setPlayedCard(card, playersPositions.get(player.getId()));
    }

    @Override
    public void setPlieOwner(BasePlayer player) {
        frame.setPlieOwner(playersPositions.get(player.getId()));
    }

    @Override
    public void setScores(int score, int opponentScore) {
        frame.setScore(score, opponentScore);
    }

    @Override
    public List<Anouncement> getAnoucement() {
        if (!frame.hasPlayerAnounced()) {
            return Collections.emptyList();
        }
        // TODO: handle stoeck properly (only communicated when playing the second card)
        return Anouncement.findAnouncements(playerHand);
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
        var p0 = winningTeam.getPlayer(0);
        p0.setName(players.get(p0.getId()).getName());
        var p1 = winningTeam.getPlayer(1);
        p1.setName(players.get(p1.getId()).getName());
        frame.displayGameResult(winningTeam);
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
        controller = new RemoteController(this);
        var connectionInfo = controller.connect(host, gameId, name);
        if (connectionInfo.error != ConnectionError.CONNECTION_SUCCESSFUL) {
            return connectionInfo.error;
        }
        id = connectionInfo.playerId;
        playersPositions.put(id, 0);
        controller.start();
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
            controller.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        controller = null;
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
