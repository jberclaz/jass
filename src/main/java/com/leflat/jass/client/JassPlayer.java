/*
 * Created for and by the FLAT(r)
 */
package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.BasePlayer;
import com.leflat.jass.server.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class JassPlayer implements IPlayer, IRemotePlayer {
    private RemoteController controller;
    private IJassUi frame;
    private int id;
    private Map<Integer, BasePlayer> players = new HashMap<>();

    public JassPlayer() {
        controller = new RemoteController(this);
        frame = new JassFrame(this);
        frame.showUi(true);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {
        players.put(player.getId(), player);
        try {
            frame.setPlayer(player, player.getId());
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
        var lock = new ReentrantLock();
        var condition = lock.newCondition();
        lock.lock();
        frame.drawCard(condition);
        try {
            condition.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return frame.getDrawnCardPosition();
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {
        try {
            frame.setDrawnCard(player, cardPosition, card);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPlayersOrder(List<BasePlayer> players) throws PlayerLeftExpection {

    }

    @Override
    public int choosePartner() throws PlayerLeftExpection {
        return 0;
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {

    }

    @Override
    public int chooseAtout(boolean first) throws PlayerLeftExpection {
        return 0;
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {

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
}
