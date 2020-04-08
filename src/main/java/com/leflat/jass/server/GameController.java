package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.stream.Collectors;

public class GameController extends Thread {
    private int gameId;
    private List<RemotePlayer> players = new ArrayList<>();
    private Team[] teams = new Team[2];       // les 2 équipes

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

    public boolean isGameFull() {
        return players.size() == 4;
    }

    public int getGameId() {
        return gameId;
    }

    @Override
    public void run() {
        System.out.println("Starting game room " + gameId);

        try {
            boolean playAnotherGame;
            do {
                chooseTeam();     // détermine les équipes

                playOneGame();

                playAnotherGame = players.get(0).getNewGame();

                for (Team team : teams) {
                    team.resetScore();
                }
            } while (playAnotherGame);

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
        } catch (BrokenRuleException e) {
            System.err.println("Error: Jass rule broken: " + e.getBrokenRule());
        }

        System.out.println("Game " + gameId + " ended");
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

            for (var p : players) {   // envoie le score
                var opponentTeam = teams[(p.getTeam().getId() + 1) % 2];
                p.setScores(p.getTeam().getScore(), opponentTeam.getScore());
            }

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
        for (var p : players) {
            p.setGameResult(winners);
        }

        /* waits a few seconds so that the players can see all the cards */
        waitSec(4);
    }

    Plie playPlie(int startingPlayer) throws PlayerLeftExpection, BrokenRuleException {
        var plie = new Plie();

        for (int i = 0; i < 4; i++) {     // demande de jouer
            var player = players.get((startingPlayer + i) % 4);
            // TODO: replace arguments with plie
            var card = player.play();

            player.getAnoucement();

            for (int j = 1; j < 4; j++) {             // envoie la carte jouée aux autres
                players.get((startingPlayer + i + j) % 4).setPlayedCard(player, card);
            }

            plie.playCard(card, player, player.getHand());
            player.removeCard(card);
        }

        /* now everybody has played ... */

        // choix et comptabilisation des annonces
        processAnoucements();

        // comptabilisation des points
        plie.getOwner().getTeam().addScore(plie.getScore());

        /* waits a few seconds so that the players can see all the cards */
        waitSec(1.5f);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return null;
        }

        for (var p : players) {
            p.collectPlie(plie.getOwner());
        }

        return plie;
    }

    void processAnoucements() throws PlayerLeftExpection {
        BasePlayer playerWithStoeck = null;
        Anouncement highestAnouncement = null;
        Team anouncingTeam = null;  // joueur qui a la plus grosse annonce
        for (var p : players) {
            for (var anouncement : p.getAnouncements()) {
                System.out.println(p + " announce : " + anouncement);
                if (anouncement.getType() == Anouncement.STOECK) {
                    playerWithStoeck = p;
                    continue;
                }
                if (highestAnouncement == null || anouncement.compareTo(highestAnouncement) > 0) {
                    highestAnouncement = anouncement;
                    anouncingTeam = p.getTeam();
                }
            }
        }

        if (anouncingTeam != null) { // there are announces
            for (var p : players) {
                if (p.getAnoucement().isEmpty()) {
                    continue;
                }
                if ((p.getTeam() == anouncingTeam)) {
                    anouncingTeam.addAnnoucementScore(p.getAnouncements(), Card.atout);
                    for (var p2 : players) {
                        p2.setAnouncement(p, p.getAnouncements());
                    }
                }
                p.clearAnouncement();
            }
        } else if (playerWithStoeck != null) { // no announce but stock
            for (var p : players) {
                p.setAnouncement(playerWithStoeck, Collections.singletonList(new Anouncement(Anouncement.STOECK, null)));
            }
            int stoeckScore = Card.atout == Card.COLOR_SPADE ? Anouncement.VALUES[Anouncement.STOECK] * 2 : Anouncement.VALUES[Anouncement.STOECK];
            // add stock points
            playerWithStoeck.getTeam().addScore(stoeckScore);
            playerWithStoeck.clearAnouncement();
        }
    }

    int chooseAtout(int playerNumber) throws PlayerLeftExpection {
        var choice = players.get(playerNumber).chooseAtout(true);   // demande de faire atout en premier
        if (choice == 4) {     // si on passe
            var second = players.get((playerNumber + 2) % 4);
            choice = second.chooseAtout(false);   // demande de faire atout en second
        }
        for (var p : players) {
            p.setAtout(choice, players.get(playerNumber));
        }
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

    private void chooseTeamsRandomly() throws PlayerLeftExpection {
        // préparation du tirage des équipes
        for (var player : players) {
            player.prepareTeamDrawing(true);
        }

        boolean drawingSuccessful;
        do {
            HashMap<BasePlayer, Card> cardsDrawn = new HashMap<>();    // cartes tirées
            var cards = Card.shuffle(36);
            for (var p : players) {
                int cardNumber = p.drawCard();

                cardsDrawn.put(p, cards.get(cardNumber));

                for (var p2 : players) {
                    p2.setCard(p, cardNumber, cards.get(cardNumber));
                }
            }
            // delay to allow players to watch cards
            waitSec(2);

            // détermine les équipes
            drawingSuccessful = calculateTeam(cardsDrawn);
            if (!drawingSuccessful) {
                for (var p : players) {
                    p.prepareTeamDrawing(false);
                }
                waitSec(2);
            }
        } while (!drawingSuccessful);
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

    private void pickTeamMates() throws PlayerLeftExpection {
        int partnerId = players.get(0).choosePartner();    // demande de choisir le partenaire
        for (var p : players) {
            if (p.getId() == partnerId || p == players.get(0)) {
                teams[0].addPlayer(p);
            } else {
                teams[1].addPlayer(p);
            }
        }
    }

    private RemotePlayer getPlayerById(int id) {
        for (var p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        throw new IndexOutOfBoundsException("Error: unknown player id " + id);
    }

    private int getPlayerPosition(BasePlayer player) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == player.getId()) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException("Player " + player.getId() + " not found");
    }

    private void reorderPlayers() {
        var tempList = List.copyOf(players);
        players.clear();
        players.add(tempList.get(teams[0].getPlayer(0).getId()));
        players.add(tempList.get(teams[1].getPlayer(0).getId()));
        players.add(tempList.get(teams[0].getPlayer(1).getId()));
        players.add(tempList.get(teams[1].getPlayer(1).getId()));
    }

    private static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
