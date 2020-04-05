package com.leflat.jass.server;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Team;
import com.leflat.jass.common.TeamSelectionMethod;

import java.util.*;
import java.util.stream.Collectors;

public class GameController extends Thread {
    private int gameId;
    private List<RemotePlayer>  players = new ArrayList<>();
    private Team[] teams = new Team[2];       // les 2 équipes
    private boolean running;

    public GameController(int id) {
        this.gameId = id;
         for (int i = 0; i < 2; i++) {      // création des équipes
            teams[i] = new Team(i);
        }
    }

    public void addPlayer(RemotePlayer newPlayer) throws PlayerLeftExpection {
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

            try {
                boolean playMore = false;
                do {
                    chooseTeam();     // détermine les équipes
/*
                    playOneGame();

                    // ask whether they want to play another part
                    playMore = players.get(0).askNewGame();

                    for (Team team : teams) {
                        team.resetScore();
                    }

 */
                } while (playMore);

            } catch (PlayerLeftExpection e) {
                var disconnectedPlayer = getPlayerById(e.getPlayerId());
                players.remove(disconnectedPlayer);
                for (var player : players) {
                    try {
                        player.playerLeft(disconnectedPlayer);
                    } catch (PlayerLeftExpection ee) {
                        System.err.println("Player " + ee.getPlayerId() + " also left.");
                    }
                }
            }

        } while (running); //  loop forever

        System.out.println("Game " + gameId + " ended");
    }

    public int getGameId() {
        return gameId;
    }

    private void chooseTeam() throws PlayerLeftExpection {
        Arrays.stream(teams).forEach(Team::reset);

        var teamChoiceMethod = players.get(0).chooseTeamSelectionMethod();
        if (teamChoiceMethod == TeamSelectionMethod.RANDOM) { // choisir au hasard
            chooseTeamsRandomly();
        } else {       // choisir son partenaire
            pickTeamMates();
        }

        reorderPlayers();

        var order = players.stream().map(BasePlayer::getId).collect(Collectors.toList());
        for (var p : players) {
            p.setPlayersOrder(order);
        }
    }

    void chooseTeamsRandomly() throws PlayerLeftExpection {
        // préparation du tirage des équipes
        for (var player : players) {
            player.prepareTeamDrawing(true);
        }

        boolean drawingSuccessful;
        do {
            HashMap<BasePlayer, Integer> cardsDrawn = new HashMap<>();    // cartes tirées
            int[] cards = shuffleCards();
            for (var p : players) {
                int cardNumber = p.drawCard();

                cardsDrawn.put(p, cards[cardNumber]);

                for (var p2 : players) {
                    p2.setCard(p, cardNumber, new Card(cards[cardNumber]));
                }
            }
            // delay to allow players to watch cards
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }

            // détermine les équipes
            drawingSuccessful = calculateTeam(cardsDrawn);
            if (!drawingSuccessful) {
                for (var p : players) {
                    p.prepareTeamDrawing(false);
                }
            }
        } while (!drawingSuccessful);
    }

    boolean calculateTeam(Map<BasePlayer, Integer> choosenCards) {
        var lowest = players.get(0);
        var highest = players.get(0);
        for (int i = 1; i < 4; i++) {
            var player = players.get(i);
            if ((choosenCards.get(player) % 9) < (choosenCards.get(lowest) % 9))
                lowest = player;
            if ((choosenCards.get(player) % 9) > (choosenCards.get(highest) % 9))
                highest = player;
        }
        boolean ok = true;
        for (BasePlayer player : players) {
            if (player != lowest) {
                if ((choosenCards.get(player) % 9) == (choosenCards.get(lowest) % 9)) {
                    ok = false;
                }
            }
            if (player != highest) {
                if ((choosenCards.get(player) % 9) == (choosenCards.get(highest) % 9)) {
                    ok = false;
                }
            }
        }
        if (!ok) return false;

        for (BasePlayer p : players) {
            if (p == lowest || p == highest) {
                p.setTeam(0);
                teams[0].addPlayer(p);
            } else {
                p.setTeam(1);
                teams[1].addPlayer(p);
            }
        }

        return true;
    }

    void pickTeamMates() throws PlayerLeftExpection {
        int partnerId = players.get(0).choosePartner();    // demande de choisir le partenaire
        for (var p : players) {
            if (p.getId() == partnerId || p == players.get(0)) {
                p.setTeam(0);
                teams[0].addPlayer(p);
            } else {
                p.setTeam(1);
                teams[1].addPlayer(p);
            }
        }
    }

    // distribue les cartes
    int[] shuffleCards() {
        final int[] cards = new int[36];
        boolean[] usedCards = new boolean[36];
        Random rand = new Random();
        for (int i = 0; i < 36; i++) {
            usedCards[i] = false;
        }
        int j;
        for (int i = 0; i < 35; i++) {
            do {
                j = (int) (rand.nextDouble() * 36);
            } while (usedCards[j]);
            cards[i] = j;
            usedCards[j] = true;
        }
        j = 0;
        while (usedCards[j]) {
            j++;
        }
        cards[35] = j;
        return cards;
    }

    RemotePlayer getPlayerById(int id) {
        for (var p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    void reorderPlayers() {
        var tempList = List.copyOf(players);
        players.clear();
        players.add(tempList.get(teams[0].getPlayer(0).getId()));
        players.add(tempList.get(teams[1].getPlayer(0).getId()));
        players.add(tempList.get(teams[0].getPlayer(1).getId()));
        players.add(tempList.get(teams[1].getPlayer(1).getId()));
    }
}
