package com.leflat.jass.test;

import com.leflat.jass.common.*;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.ServerNetwork;
import com.leflat.jass.server.RemotePlayer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class TestSingleClient {
    private RemotePlayer mainPlayer;
    private List<BasePlayer> players = new ArrayList<>();
    private Team[] teams = new Team[2];

    class TestPlayer extends BasePlayer {
        public TestPlayer(int id, String name) {
            super(id);
            this.name = name;
        }
    }


    public static final int PORT = 23107;

    public TestSingleClient() throws IOException, PlayerLeftExpection, BrokenRuleException {
        ServerSocket serverSocket;
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Started test server on port " + PORT);
            clientSocket = serverSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        var network = new ServerNetwork(clientSocket);
        network.receiveRawMessage();
        network.sendMessage(String.valueOf(123456));

        mainPlayer = new RemotePlayer(0, new ServerNetwork(clientSocket));

        players.add(mainPlayer);
        players.add(new TestPlayer(1, "Berte"));
        players.add(new TestPlayer(2, "Pischus"));
        players.add(new TestPlayer(3, "GC"));

        for (int i = 0; i < 2; i++) {
            teams[i] = new Team(i);
            teams[i].reset();
        }

        for (var p : players) {
            mainPlayer.setPlayerInfo(p);
        }

        boolean playAnotherGame;
        do {

            chooseTeam();

            playOneGame();

            playAnotherGame = mainPlayer.getNewGame();

            for (Team team : teams) {
                team.resetScore();
            }
        } while (playAnotherGame);

        waitSec(30);
    }

    private void playOneGame() throws PlayerLeftExpection, BrokenRuleException {
        int firstToPlay = drawCards();
        Plie plie = null;
        do {
            Card.atout = chooseAtout(firstToPlay);                     // choisit l'atout
            int nextPlayer = firstToPlay;

            for (int i = 0; i < 9; i++) {
                plie = playPlie(nextPlayer);
                if (plie == null) {
                    break;
                }
                nextPlayer = getPlayerPosition(plie.getOwner());
            }

            if (plie != null) {    // si personne n'a gagné : on continue normalement
                // 5 de der
                players.get(nextPlayer).getTeam().addScore(Card.atout == Card.COLOR_SPADE ? 10 : 5);
            }


            var opponentTeam = teams[(mainPlayer.getTeam().getId() + 1) % 2];
            mainPlayer.setScores(mainPlayer.getTeam().getScore(), opponentTeam.getScore());

            /* waits a few seconds so that the players can see the last
             * cards and the score */
            waitSec(2);

            if (plie != null) {
                firstToPlay = (firstToPlay + 1) % 4;
                drawCards();
            }
            // répète jusqu'à ce qu'on gagne
        } while (plie != null);

        // Sends the winner to all player
        var winners = teams[0].hasWon() ? teams[0] : teams[1];
        mainPlayer.setGameResult(winners);

        /* waits a few seconds so that the players can see all the cards */
        waitSec(4);
    }

    Plie playPlie(int startingPlayer) throws PlayerLeftExpection, BrokenRuleException {
        var plie = new Plie();

        for (int i = 0; i < 4; i++) {     // demande de jouer
            var player = players.get((startingPlayer + i) % 4);
            // TODO: replace arguments with plie
            Card card;
            if (player.getId() == 0) {
                card = mainPlayer.play();

                mainPlayer.getAnoucement();

                plie.playCard(card, player, player.getHand());
            } else {
                Random rand = new Random();
                while (true) {
                    int cardNumber = rand.nextInt(player.getHand().size());
                    card = player.getHand().get(cardNumber);
                    try {
                        plie.playCard(card, player, player.getHand());
                        waitSec(1);
                        break;
                    } catch (BrokenRuleException e) {

                    }
                }
                mainPlayer.setPlayedCard(player, card);
            }

            player.removeCard(card);
        }

        /* now everybody has played ... */

        // comptabilisation des points
        plie.getOwner().getTeam().addScore(plie.getScore());

        /* waits a few seconds so that the players can see all the cards */
        waitSec(1.5f);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return null;
        }

        mainPlayer.collectPlie(plie.getOwner());

        return plie;
    }

    int chooseAtout(int playerNumber) throws PlayerLeftExpection {
        var first = players.get(playerNumber);
        int choice;
        Random rand = new Random();
        if (first.getId() == 0) {
            choice = mainPlayer.chooseAtout(true);   // demande de faire atout en premier
        } else {
            choice = rand.nextInt(5);
            waitSec(1);
        }

        if (choice == 4) {     // si on passe
            var second = players.get((playerNumber + 2) % 4);
            if (second.getId() == 0) {
                choice = mainPlayer.chooseAtout(false);   // demande de faire atout en second
            } else {
                choice = rand.nextInt(4);
                waitSec(1);
            }
        }

        mainPlayer.setAtout(choice, players.get(playerNumber));

        return choice;
    }

    int drawCards() throws PlayerLeftExpection {
        int playerWithDiamondSeven = 0;      // 7 de carreau
        var cards = Card.shuffle(36);     // choisir les cartes au hasard
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 9; j++) {
                if (cards.get(i * 9 + j).getNumber() == Card.DIAMOND_SEVEN) {    // 7 de carreau
                    playerWithDiamondSeven = i;
                    break;
                }
            }
            players.get(i).setHand(cards.subList(i * 9, (i + 1) * 9));
        }
        return playerWithDiamondSeven;
    }

    private void chooseTeam() throws PlayerLeftExpection {
        var choice = mainPlayer.chooseTeamSelectionMethod();
        if (choice == TeamSelectionMethod.RANDOM) {
            mainPlayer.prepareTeamDrawing(true);
            boolean drawingSuccessful;
            do {
                Map<BasePlayer, Card> cardsDrawn = new HashMap<>();    // cartes tirées
                var cards = Card.shuffle(36);
                int cardNumber = mainPlayer.drawCard();

                cardsDrawn.put(mainPlayer, cards.get(cardNumber));
                mainPlayer.setCard(mainPlayer, cardNumber, cards.get(cardNumber));

                Random rand = new Random();
                for (int i = 1; i < 4; i++) {
                    var p = players.get(i);
                    do {
                        cardNumber = rand.nextInt(36);
                    } while (cardsDrawn.containsValue(cards.get(cardNumber)));

                    cardsDrawn.put(p, cards.get(cardNumber));

                    waitSec(0.5f);
                    mainPlayer.setCard(p, cardNumber, cards.get(cardNumber));
                }
                // delay to allow players to watch cards
                waitSec(2);

                // détermine les équipes
                drawingSuccessful = calculateTeam(cardsDrawn);
                if (!drawingSuccessful) {
                    mainPlayer.prepareTeamDrawing(false);
                    waitSec(2);
                }
            } while (!drawingSuccessful);
        } else {       // choisir son partenaire
            int partnerId = mainPlayer.choosePartner();    // demande de choisir le partenaire
            for (var p : players) {
                if (p.getId() == partnerId || p == players.get(0)) {
                    teams[0].addPlayer(p);
                } else {
                    teams[1].addPlayer(p);
                }
            }
        }

        reorderPlayers();

        var order = players.stream().map(BasePlayer::getId).collect(Collectors.toList());
        mainPlayer.setPlayersOrder(order);
    }

    private boolean calculateTeam(Map<BasePlayer, Card> choosenCards) {
        var lowest = players.get(0);
        var highest = players.get(0);
        for (int i = 1; i < 4; i++) {
            var player = players.get(i);
            if (choosenCards.get(player).getRank() < choosenCards.get(lowest).getRank())
                lowest = player;
            if (choosenCards.get(player).getRank() > choosenCards.get(highest).getRank())
                highest = player;
        }
        boolean ok = true;
        for (BasePlayer player : players) {
            if (player != lowest) {
                if (choosenCards.get(player).getRank() == choosenCards.get(lowest).getRank()) {
                    ok = false;
                }
            }
            if (player != highest) {
                if (choosenCards.get(player).getRank() == choosenCards.get(highest).getRank()) {
                    ok = false;
                }
            }
        }
        if (!ok) return false;

        for (BasePlayer p : players) {
            if (p == lowest || p == highest) {
                teams[0].addPlayer(p);
            } else {
                teams[1].addPlayer(p);
            }
        }

        return true;
    }

    private void reorderPlayers() {
        var tempList = List.copyOf(players);
        players.clear();
        players.add(tempList.get(teams[0].getPlayer(0).getId()));
        players.add(tempList.get(teams[1].getPlayer(0).getId()));
        players.add(tempList.get(teams[0].getPlayer(1).getId()));
        players.add(tempList.get(teams[1].getPlayer(1).getId()));
    }

    private int getPlayerPosition(BasePlayer player) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == player.getId()) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException("Player " + player.getId() + " not found");
    }

    private static void waitSec(float seconds) {
        try {
            Thread.sleep((int) (seconds * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            new TestSingleClient();
        } catch (IOException | PlayerLeftExpection | BrokenRuleException e) {
            e.printStackTrace();
        }
    }
}
