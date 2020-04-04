package com.leflat.jass.server;

import com.leflat.jass.common.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.List;

public class GameController extends Thread {
    private int gameId;
    private List<BasePlayer> players = new ArrayList<>();
    private boolean running;

    public GameController(int id) {
        this.gameId = id;
    }

    public void addPlayer(BasePlayer newPlayer) throws PlayerLeftExpection {
        assert players.size() < 4;
        for (var p : players) {
            p.setPlayerInfo(newPlayer);
            newPlayer.setPlayerInfo(p);
        }
        players.add(newPlayer);
    }

    public int getNbrPlayers() {
        return players.size();
    }

    public boolean fullGame() {
        return players.size() == 4;
    }

    @Override
    public void run() {
        System.out.println("Starting game room " + gameId);
        running = true;
        do {
            /*
            try {
                boolean playMore;
                do {
                    chooseTeam();     // détermine les équipes

                    playOneGame();

                    // ask whether they want to play another part
                    playMore = players.get(0).askNewGame();

                    for (Team team : teams) {
                        team.resetScore();
                    }
                } while (playMore);

            } catch (ClientLeftException e) {
                BasePlayer disconnectedPlayer = getPlayerById(e.getClientId());
                players.remove(disconnectedPlayer);
                for (var player : players) {
                    try {
                        player.sendPlayerLeft(disconnectedPlayer.getId());
                    } catch (ClientLeftException ee) {
                        System.err.println("Player " + ee.getClientId() + " also left.");
                    }
                }
            }
             */
        } while (running); //  loop forever

        System.out.println("Game " + gameId + " ended");
    }

    public int getGameId() { return gameId; }
}
