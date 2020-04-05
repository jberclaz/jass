/*
 * Created for and by the FLAT(r)
 */
package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.Team;

import java.util.*;
import java.util.stream.Collectors;

public class JassPlayer implements IPlayer, IRemotePlayer {
    private RemoteController controller;
    private IJassUi frame;
    private int id;
    private Map<Integer, Integer> playersPositions = new HashMap<>();
    private Map<Integer, BasePlayer> players = new HashMap<>();


    public JassPlayer() {
        controller = new RemoteController(this);
        frame = new JassFrame(this);
        frame.showUi(true);

        // TODO: remove (DEBUG)
        Random rnd = new Random();
        connect(String.valueOf(rnd.nextInt(100)), "localhost", 0);
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
        for (int i=0; i<playerIds.size(); i++) {
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
        frame.setPlayerCards(cards);
    }

    @Override
    public int chooseAtout(boolean first) {
        return frame.chooseAtout(first);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {
        frame.setAtout(color, playersPositions.get(firstToPlay.getId()));
    }

    @Override
    public void play(int currentColor, int highestRank, boolean cut) throws PlayerLeftExpection {

    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {

    }

    @Override
    public void setPlieOwner(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {

    }

    @Override
    public List<Anouncement> getAnoucement() throws PlayerLeftExpection {
        return null;
    }

    @Override
    public void setAnouncement(BasePlayer player, List<Anouncement> anouncements) throws PlayerLeftExpection {

    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {

    }

    @Override
    public boolean getNewGame() throws PlayerLeftExpection {
        return false;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public int connect(String name, String host, int gameId) {
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
        return true;
    }

    @Override
    public boolean isConnected() {
        return controller.isConnected();
    }

    public int getInitialRelativePosition(BasePlayer player) {
        return (player.getId() - id + 4) % 4;
    }
}
