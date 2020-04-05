package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.stream.Collectors;

public class GameController extends Thread {
    private int gameId;
    private List<RemotePlayer> players = new ArrayList<>();
    private Team[] teams = new Team[2];       // les 2 équipes
    private int atout;

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
            boolean playAnotherGame = false;
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
        }

        System.out.println("Game " + gameId + " ended");
    }

    private void playOneGame() throws PlayerLeftExpection {
        /* randomly choose the cards and send them */
        // celui qui commence la partie et fait atout

        int firstToPlay = drawCards();
        int nextPlayer;
        do {
            atout = chooseAtout(firstToPlay);                     // choisit l'atout
            nextPlayer = firstToPlay;

            int plieNumber = 0;
            while ((plieNumber < 9) && (nextPlayer >= 0)) {   // fait jouer les 9 plies
                nextPlayer = playPlie(nextPlayer);
                plieNumber++;
            }

            if (nextPlayer >= 0) {    // si personne n'a gagné : on continue normalement
                // 5 de der
                teams[currentPlie..getTeam()].addScore(atout == Card.COLOR_SPADE ? 10 : 5);

                for (var p : players) {   // envoie le score
                    p.sendScore(teams[p.getTeam()].getScore(), teams[(p.getTeam() + 1) % 2].getScore());
                }

                /* waits a few seconds so that the players can see the last
                 * cards and the score */
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }

                firstToPlay = (firstToPlay + 1) % 4;
                drawCards();
            } else { // si une équipe a gagné
                for (var p : players) {   // envoie le score
                    p.sendScore(teams[p.getTeam()].getScore(),
                            teams[(p.getTeam() + 1) % 2].getScore());
                }
            }

            // répète jusqu'à ce qu'on gagne
        } while (!teams[0].hasWon() && !teams[1].hasWon());

        // Sends the winner to all player
        int winner = teams[0].hasWon() ? 0 : 1;
        for (var p : players) {
            p.sendWinner(winner, teams[winner].getPlayer(0).getId(), teams[winner].getPlayer(1).getId());
        }

        /* waits a few seconds so that the players can see all the cards */
        try {
            Thread.sleep(4000);
        } catch (InterruptedException ignored) {
        }
    }

    int playPlie(int startingPlayer) throws PlayerLeftExpection {
        var player = players.get(startingPlayer);
        var cardPlayed = player.play(-1, -1, false);
        var currentPlie = new Plie(cardPlayed, atout, player);

        for (int i = 1; i < 4; i++) {           // envoie la carte jouée aux autres
            players.get((startingPlayer + i) % 4).setPlayedCard(player, cardPlayed);
        }

        var announcements = player.getAnoucement();
        processAnnouncements(player, announcements);

        for (int i = 1; i < 4; i++) {     // demande de jouer
            player = players.get((startingPlayer + i) % 4);
            cardPlayed = player.play(currentPlie.highest, currentPlie.color, currentPlie.cut);

            var announcement = player.getAnoucement();
            processAnnouncements(player, announcement);

            for (int j = 1; j < 4; j++) {             // envoie la carte jouée aux autres
                players.get((startingPlayer + i + j) % 4).setPlayedCard(player, cardPlayed);
            }
            processCard(cardPlayed, (startingPlayer + i) % 4, nextAnswer[1]);
        }

        /* now everybody has played ... */

        /* waits a few seconds so that the players can see all the cards */
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }

        // communique qui a pris la plie
        for (var p : players) {
            p.setPlieOwner(currentPlie.owner);
        }

        // choix et comptabilisation des annonces
        handleAllAnnoucements();

        // comptabilisation des points
        player = currentPlie.owner;
        teams[player.getTeam()].addScore(atout == Card.COLOR_SPADE ? currentPlie.score * 2 : currentPlie.score);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return -1;
        }

        return currentPlie.owner;
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
        var cards = shuffleCards();     // choisir les cartes au hasard
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
            var cards = shuffleCards();
            for (var p : players) {
                int cardNumber = p.drawCard();

                cardsDrawn.put(p, cards.get(cardNumber));

                for (var p2 : players) {
                    p2.setCard(p, cardNumber, cards.get(cardNumber));
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
                p.setTeam(0);
                teams[0].addPlayer(p);
            } else {
                p.setTeam(1);
                teams[1].addPlayer(p);
            }
        }

        return true;
    }

    private void pickTeamMates() throws PlayerLeftExpection {
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
    private List<Card> shuffleCards() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            cards.add(new Card(i));
        }
        Random rand = new Random();
        for (int i = 35; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            if (i != j) {
                Collections.swap(cards, j, i);
            }
        }
        return cards;
    }

    private RemotePlayer getPlayerById(int id) {
        for (var p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        System.err.println("Error: unknown player id " + id);
        return null;
    }

    private void reorderPlayers() {
        var tempList = List.copyOf(players);
        players.clear();
        players.add(tempList.get(teams[0].getPlayer(0).getId()));
        players.add(tempList.get(teams[1].getPlayer(0).getId()));
        players.add(tempList.get(teams[0].getPlayer(1).getId()));
        players.add(tempList.get(teams[1].getPlayer(1).getId()));
    }
}
