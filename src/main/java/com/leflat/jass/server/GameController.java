package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GameController extends Thread {
    private int gameId;
    private List<AbstractRemotePlayer> players = new ArrayList<>();
    private Team[] teams = new Team[2];       // les 2 équipes
    private boolean noWait = false;
    private final static Logger LOGGER = Logger.getLogger(GameController.class.getName());

    public GameController(int id) {
        this.gameId = id;
        for (int i = 0; i < 2; i++) {      // création des équipes
            teams[i] = new Team(i);
        }
    }

    public void addPlayer(AbstractRemotePlayer newPlayer) throws PlayerLeftExpection {
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

    public void setNoWait(boolean enable) {
        noWait = enable;
    }

    public Team[] getTeams() {
        return teams;
    }

    @Override
    public void run() {
        LOGGER.info("Starting game room " + gameId);

        BasePlayer disconnectedPlayer = null;
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
            LOGGER.log(Level.WARNING, "Player " + e.playerId + " left the game", e);
            disconnectedPlayer = getPlayerById(e.getPlayerId());
            players.remove(disconnectedPlayer);
        } catch (BrokenRuleException e) {
            LOGGER.severe("Error: Jass rule broken: " + e.getBrokenRule());
        } finally {
            for (var player : players) {
                try {
                    player.playerLeft(disconnectedPlayer == null ? players.get(0) : disconnectedPlayer);
                } catch (PlayerLeftExpection ee) {
                    LOGGER.warning("Player " + ee.getPlayerId() + " also left.");
                }
            }
        }

        LOGGER.info("Game " + gameId + " ended");
    }

    void playOneGame() throws PlayerLeftExpection, BrokenRuleException {
        int firstToPlay = drawCards();
        Plie plie;
        do {
            plie = playOneHand(firstToPlay);

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
        sendResultAsync(winners);

        /* waits a few seconds so that the players can see all the cards */
        waitSec(4);
    }

    Plie playOneHand(int firstToPlay) throws PlayerLeftExpection, BrokenRuleException {
        int nextPlayer = firstToPlay;
        Plie plie = null;
        int[] handScores = new int[2];

        Arrays.stream(teams).forEach(Team::resetPlies);
        Card.atout = chooseAtout(firstToPlay);

        for (int i = 0; i < 9; i++) {
            plie = playPlie(nextPlayer);
            if (plie == null) {
                break;
            }
            nextPlayer = getPlayerPosition(plie.getOwner());
            plie.getOwner().getTeam().addPlie();
            handScores[plie.getOwner().getTeam().getId()] += plie.getScore();
        }

        if (plie != null) {    // si personne n'a gagné : on continue normalement
            // 5 de der
            var cinqDeDer = Card.atout == Card.COLOR_SPADE ? 10 : 5;
            var team = players.get(nextPlayer).getTeam();
            team.addScore(cinqDeDer);
            handScores[team.getId()] += cinqDeDer;
        }

        Team match = null;
        for (var team : teams) {
            if (team.getNumberOfPlies() == 9) {
                // match
                team.addScore(Card.atout == Card.COLOR_SPADE ? 200 : 100);
                match = team;
            }
        }
        setHandScoreAsync(handScores, match);
        waitSec(2);

        return plie;
    }

    Plie playPlie(int startingPlayer) throws PlayerLeftExpection, BrokenRuleException {
        var plie = new Plie();

        for (int i = 0; i < 4; i++) {     // demande de jouer
            var player = players.get((startingPlayer + i) % 4);
            var card = player.play();

            for (int j = 1; j < 4; j++) {             // envoie la carte jouée aux autres
                players.get((startingPlayer + i + j) % 4).setPlayedCard(player, card);
            }

            plie.playCard(card, player, player.getHand());
            player.removeCard(card);
        }

        /* now everybody has played ... */

        // choix et comptabilisation des annonces
        if (processAnnouncements()) {
            waitSec(2f);
        }

        // comptabilisation des points
        plie.getOwner().getTeam().addScore(plie.getScore());

        /* waits a few seconds so that the players can see all the cards */
        waitSec(2f);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return null;
        }

        for (var p : players) {
            p.collectPlie(plie.getOwner());
        }

        return plie;
    }

    boolean processAnnouncements() throws PlayerLeftExpection {
        boolean validAnnoucements = false;
        Map<Integer, List<Announcement>> annoucements = new HashMap<>();
        BasePlayer playerWithStoeck = null;
        Announcement highestAnnouncement = null;
        Team announcingTeam = null;  // joueur qui a la plus grosse annonce
        for (var p : players) {
            var a = p.getAnnouncements();
            if (a.isEmpty()) {
                continue;
            }
            annoucements.put(p.getId(), a);
            for (var announcement : a) {
                LOGGER.info(p + " announces : " + announcement);
                if (announcement.getType() == Announcement.STOECK) {
                    playerWithStoeck = p;
                    continue;
                }
                if (highestAnnouncement == null || announcement.compareTo(highestAnnouncement) > 0) {
                    highestAnnouncement = announcement;
                    announcingTeam = p.getTeam();
                }
            }
        }

        if (announcingTeam != null) { // there are announces
            for (var p : players) {
                if (!annoucements.containsKey(p.getId())) {
                    continue;
                }
                if ((p.getTeam() == announcingTeam)) {
                    announcingTeam.addAnnoucementScore(annoucements.get(p.getId()));
                    for (var p2 : players) {
                        p2.setAnnouncements(p, annoucements.get(p.getId()));
                    }
                    validAnnoucements = true;
                }
                p.clearAnnouncements();
            }
        } else if (playerWithStoeck != null) { // no announce but stock
            for (var p : players) {
                p.setAnnouncements(playerWithStoeck, Collections.singletonList(new Announcement(Announcement.STOECK, null)));
            }
            int stoeckScore = Card.atout == Card.COLOR_SPADE ? Announcement.VALUES[Announcement.STOECK] * 2 : Announcement.VALUES[Announcement.STOECK];
            // add stock points
            playerWithStoeck.getTeam().addScore(stoeckScore);
            playerWithStoeck.clearAnnouncements();
            validAnnoucements = true;
        }
        return validAnnoucements;
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

    void chooseTeam() throws PlayerLeftExpection {
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

    boolean calculateTeam(Map<BasePlayer, Card> choosenCards) {
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

    void pickTeamMates() throws PlayerLeftExpection {
        int partnerId = players.get(0).choosePartner();    // demande de choisir le partenaire
        for (var p : players) {
            if (p.getId() == partnerId || p == players.get(0)) {
                teams[0].addPlayer(p);
            } else {
                teams[1].addPlayer(p);
            }
        }
    }

    AbstractRemotePlayer getPlayerById(int id) {
        for (var p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        throw new IndexOutOfBoundsException("Error: unknown player id " + id);
    }

    int getPlayerPosition(BasePlayer player) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == player.getId()) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException("Player " + player.getId() + " not found");
    }

    void reorderPlayers() {
        var tempList = List.copyOf(players);
        players.clear();
        players.add(tempList.get(teams[0].getPlayer(0).getId()));
        players.add(tempList.get(teams[1].getPlayer(0).getId()));
        players.add(tempList.get(teams[0].getPlayer(1).getId()));
        players.add(tempList.get(teams[1].getPlayer(1).getId()));
    }

    void waitSec(float seconds) {
        if (noWait) {
            return;
        }
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }

    void setHandScoreAsync(int[] handScore, Team match) throws PlayerLeftExpection {
        List<PlayerLeftExpection> exceptions = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (var p : players) {
            var thread = new Thread(() -> {
                try {
                    int ourTeam = p.getTeam().getId();
                    p.setHandScore(handScore[ourTeam], handScore[(ourTeam + 1) % 2], match);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    exceptions.add(playerLeftExpection);
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (var thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (var exception : exceptions) {
            throw exception;
        }
    }

    void sendResultAsync(Team team) throws PlayerLeftExpection {
        List<PlayerLeftExpection> exceptions = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (var p : players) {
            var thread = new Thread(() -> {
                try {
                    p.setGameResult(team);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    exceptions.add(playerLeftExpection);
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (var thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (var exception : exceptions) {
            throw exception;
        }
    }
}
