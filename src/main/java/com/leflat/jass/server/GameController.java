package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GameController extends Thread {
    private final int gameId;
    private final List<AbstractRemotePlayer> players = new ArrayList<>();
    private final Team[] teams = new Team[2];       // les 2 équipes
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
                setTeamsScoreAsync(true);

                chooseTeam();     // détermine les équipes

                playOneGame();

                playAnotherGame = getPlayerById(0).getNewGame();

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

            setTeamsScoreAsync(false);

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
            var finalTeam = players.get(nextPlayer).getTeam();
            finalTeam.addScore(cinqDeDer);
            handScores[finalTeam.getId()] += cinqDeDer;

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
        }

        return plie;
    }

    Plie playPlie(int startingPlayer) throws PlayerLeftExpection, BrokenRuleException {
        var plie = new Plie();

        for (int i = 0; i < 4; i++) {       // demande de jouer
            var player = players.get((startingPlayer + i) % 4);
            var card = player.play();

            setPlayedCardAsync(player, card);

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

        collectPlieAsync(plie.getOwner());

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

        var teamChoiceMethod = getPlayerById(0).chooseTeamSelectionMethod();
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
        var firstPlayerConnected = getPlayerById(0);
        int partnerId = firstPlayerConnected.choosePartner();    // demande de choisir le partenaire
        for (var p : players) {
            if (p.getId() == partnerId || p == firstPlayerConnected) {
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
        return players.indexOf(player);
    }

    void reorderPlayers() {
        players.clear();
        players.add((AbstractRemotePlayer) teams[0].getPlayer(0));
        players.add((AbstractRemotePlayer) teams[1].getPlayer(0));
        players.add((AbstractRemotePlayer) teams[0].getPlayer(1));
        players.add((AbstractRemotePlayer) teams[1].getPlayer(1));
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
        var answers = players.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        int ourTeam = p.getTeam().getId();
                        p.setHandScore(handScore[ourTeam], handScore[(ourTeam + 1) % 2], match);
                    } catch (PlayerLeftExpection playerLeftExpection) {
                        throw new CompletionException(playerLeftExpection);
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        try {
            answers.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException ex) {
            throw (PlayerLeftExpection) ex.getCause();
        }
    }

    void sendResultAsync(Team team) throws PlayerLeftExpection {
        var answers = players.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        p.setGameResult(team);
                    } catch (PlayerLeftExpection playerLeftExpection) {
                        throw new CompletionException(playerLeftExpection);
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        try {
            answers.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException ex) {
            throw (PlayerLeftExpection) ex.getCause();
        }
    }

    void setTeamsScoreAsync(boolean reset) throws PlayerLeftExpection {
        var answers = players.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        int ourScore = 0, theirScore = 0;
                        if (!reset) {
                            var ourTeam = p.getTeam();
                            ourScore = ourTeam.getScore();
                            int otherTeamId = (ourTeam.getId() + 1) % 2;
                            theirScore = teams[otherTeamId].getScore();
                        }
                        p.setScores(ourScore, theirScore);
                    } catch (PlayerLeftExpection playerLeftExpection) {
                        throw new CompletionException(playerLeftExpection);
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        try {
            answers.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException ex) {
            throw (PlayerLeftExpection) ex.getCause();
        }
    }

    void setPlayedCardAsync(BasePlayer player, Card card) throws PlayerLeftExpection {
        var answers = players.stream()
                .filter(p -> p.getId() != player.getId())
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        p.setPlayedCard(player, card);
                    } catch (PlayerLeftExpection playerLeftExpection) {
                        throw new CompletionException(playerLeftExpection);
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        try {
            answers.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException ex) {
            throw (PlayerLeftExpection) ex.getCause();
        }
    }

     void collectPlieAsync(BasePlayer player) throws PlayerLeftExpection {
        var answers = players.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        p.collectPlie(player);
                    } catch (PlayerLeftExpection playerLeftExpection) {
                        throw new CompletionException(playerLeftExpection);
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        try {
            answers.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException ex) {
            throw (PlayerLeftExpection) ex.getCause();
        }
    }

}
