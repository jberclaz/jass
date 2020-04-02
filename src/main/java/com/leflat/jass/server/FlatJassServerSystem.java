//Title:        FlatJassServer
//Version:      1.2
//Copyright:    Copyright (c) 1998
//Author:       Pierre Métrailler & Jérome Berclaz
//Company:      Flat(r)
//Description:  This is the server for the Jass Card Game made by and
//              for the proud members of the FLAT(r)
//
//              Long life to the FLAT(r)!

package com.leflat.jass.server;


import java.net.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.leflat.jass.common.*;

public class FlatJassServerSystem {
    private int atout;
    private int lastPlayerId;    // nombre de joueurs connectés
    private Plie currentPlie;        // plie en cours
    private List<Player> players = new ArrayList<>(); // les 4 joueurs
    private Team[] teams = new Team[2];       // les 2 équipes
    private List<Player> tableOrder = new ArrayList<>();

    private ServerSocket myServerSocket = null;

    public static final int DEFAULT_PORT_NUMBER = 32107;


    public FlatJassServerSystem() {
        this(DEFAULT_PORT_NUMBER);
    }

    public FlatJassServerSystem(int port) {

        for (int i = 0; i < 2; i++) {      // création des équipes
            teams[i] = new Team();
        }
        currentPlie = new Plie();

        System.out.println("Flat Jass System Server");
        System.out.println("Version 1.2");
        System.out.println("(c) 2000-2002 by FLAT(r)");
        System.out.println();

        lastPlayerId = 0;

        // Create server socket
        try {
            myServerSocket = new ServerSocket(port);
            System.out.println("Server socket created on port " + port);
        } catch (IOException e) {
            System.err.println("ERROR: cannot create server socket");
            System.exit(1);
        }
    }

    public void run() {
        do {
            try {
                while (players.size() < 4) {  // attend 4 connexions
                    Player newPlayer = waitForConnection();
                    players.add(newPlayer);
                }
            } catch (ClientLeftException e) {
                Player disconnectedPlayer = getPlayerById(e.getClientId());
                players.remove(disconnectedPlayer);
                for (Player player : players) {
                    try {
                        player.sendPlayerLeft(disconnectedPlayer.getId());
                    } catch (ClientLeftException ee) {
                        System.err.println("Player " + ee.getClientId() + " also left.");
                    }
                }
                continue;
            }

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
                Player disconnectedPlayer = getPlayerById(e.getClientId());
                players.remove(disconnectedPlayer);
                for (var player : players) {
                    try {
                        player.sendPlayerLeft(disconnectedPlayer.getId());
                    } catch (ClientLeftException ee) {
                        System.err.println("Player " + ee.getClientId() + " also left.");
                    }
                }
            }
        } while (true); //  loop forever
    }

    // attend la connexion d'un joueur
    public Player waitForConnection() throws ClientLeftException {
        var clientConnection = new ServerNetwork();
        if (clientConnection.connect(myServerSocket)) {
            // la connexion a réussi
            clientConnection.setClientId(lastPlayerId);

            var newPlayer = new Player(lastPlayerId, clientConnection);
            System.out.println(newPlayer.getFirstName() + " " + newPlayer.getLastName() + " is connected");

            for (var player : players) {
                // infos SUR LES joueurs déjà connectés
                newPlayer.sendPlayerInfo(player);

                // infos AUX joueurs déjà connectés
                player.sendPlayerInfo(newPlayer);
            }
            lastPlayerId++;
            return newPlayer;
        }
        return null;
    }

    public static void main(String[] args) {
        Integer port = null;
        if (args.length > 1) {
            if (args[0].compareTo("-p") == 0) {
                port = Integer.valueOf(args[1]);
            } else {
                System.out.println("Syntax : java leflat.jass.server.FlatJassServerSystem -p <port_number>");
                System.exit(-1);
            }
        }
        FlatJassServerSystem flatJassServerSystem;
        if (port == null) {
            flatJassServerSystem = new FlatJassServerSystem();
        } else {
            flatJassServerSystem = new FlatJassServerSystem(port);
        }

        flatJassServerSystem.run();
    }


    private void chooseTeam() throws ClientLeftException {
        Arrays.stream(teams).forEach(Team::reset);

        int teamChoiceMethod = players.get(0).chooseTeamSelectionMethod();
        if (teamChoiceMethod == 1) { // choisir au hasard
            chooseTeamsRandomly();
        } else {       // choisir son partenaire
            pickTeamMates();
        }

        reorderPlayers();

        var idOrder = tableOrder.stream().map(Player::getId).collect(Collectors.toList());
        for (var p : players) {
            p.sendPlayerOrder(idOrder);
        }
    }

    void chooseTeamsRandomly() throws ClientLeftException {
        // préparation du tirage des équipes
        for (Player player : players) {
            player.prepareTeamChoice();
        }

        boolean drawingSuccessful;
        HashMap<Player, Integer> cardsDrawn = new HashMap<>();    // cartes tirées
        do {
            int[] cards = shuffleCards();
            for (Player p : players) {
                int cardNumber = p.drawCard();

                cardsDrawn.put(p, cards[cardNumber]);

                for (var p2 : players) {
                    p2.communicateCard(p.getId(), cardNumber, cardsDrawn.get(p));
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
                    p.chooseTeamAgain();
                }
            }
        } while (!drawingSuccessful);
    }

    void pickTeamMates() throws ClientLeftException {
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

    // organise les joueurs et les systemnetworks
    void reorderPlayers() {
        tableOrder.clear();
        tableOrder.add(teams[0].getPlayer(0));
        tableOrder.add(teams[1].getPlayer(0));
        tableOrder.add(teams[0].getPlayer(1));
        tableOrder.add(teams[1].getPlayer(1));
    }


    boolean calculateTeam(Map<Player, Integer> choosenCards) {
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
        for (Player player : players) {
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

        for (Player p : players) {
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


    void playOneGame() throws ClientLeftException {
        /* randomly choose the cards and send them */
        // celui qui commence la partie et fait atout
        int firstToPlay = drawCards();
        int nextPlayer;
        do {
            atout = chooseAtout(firstToPlay);                     // choisit l'atout
            nextPlayer = firstToPlay;

            int plieNumber = 0;
            while ((plieNumber < 9) && (nextPlayer != -1)) {   // fait jouer les 9 plies
                nextPlayer = playPlie(nextPlayer);
                plieNumber++;
            }

            if (nextPlayer != -1) {    // si personne n'a gagné : on continue normalement
                // 5 de der
                var player = tableOrder.get(currentPlie.owner);
                teams[player.getTeam()].addScore(atout == Card.COLOR_SPADE ? 10 : 5);

                for (var p : players) {   // envoie le score
                    p.sendScore(teams[p.getTeam()].getScore(), teams[(p.getTeam() + 1) % 2].getScore());
                }

                /* waits a few seconds so that the players can see the last
                 * cards and the score */
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
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
        } catch (InterruptedException e) {
        }
    }


    int chooseAtout(int playerNumber) throws ClientLeftException {
        Player first = tableOrder.get(playerNumber);
        var choice = first.chooseAtout();   // demande de faire atout en premier
        if (choice == 4) {     // si on passe
            var second = tableOrder.get((playerNumber + 2) % 4);
            choice = second.chooseAtoutSecond();   // demande de faire atout en second
        }
        for (var p : players) {
            p.communicateAtout(choice);
        }
        return choice;
    }


    int drawCards() throws ClientLeftException {
        int playerWithDiamondSeven = 0;      // 7 de carreau
        int[] cards = shuffleCards();     // choisir les cartes au hasard
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 9; j++) {
                if (cards[i * 9 + j] == Card.DIAMOND_SEVEN) {    // 7 de carreau
                    playerWithDiamondSeven = i;
                    break;
                }
            }
            tableOrder.get(i).sendHand(Arrays.copyOfRange(cards, i * 9, (i + 1) * 9));
        }
        return playerWithDiamondSeven;
    }


    int playPlie(int startingPlayer) throws ClientLeftException {
        var player = tableOrder.get(startingPlayer);
        var answer = player.playFirst();
        var card = new Card(answer[0]);
        currentPlie.color = card.getColor();
        currentPlie.highest = card.getRank();
        currentPlie.score = answer[1];
        currentPlie.owner = startingPlayer;
        currentPlie.cut = false;

        for (int i = 1; i < 4; i++) {           // envoie la carte jouée aux autres
            tableOrder.get((startingPlayer + i) % 4).sendPlayedCard(startingPlayer, card);
        }

        int announcement = answer[2]; // Annonces ?
        processAnnouncements(player, announcement);

        for (int i = 1; i < 4; i++) {     // demande de jouer
            player = tableOrder.get((startingPlayer + i) % 4);
            var nextAnswer = player.playNext(currentPlie.highest, currentPlie.color, currentPlie.cut);
            var playedCard = new Card(nextAnswer[0]);

            announcement = nextAnswer[2]; // Annonces ?
            processAnnouncements(player, announcement);

            for (int j = 1; j < 4; j++) {             // envoie la carte jouée aux autres
                tableOrder.get((startingPlayer + i + j) % 4).sendPlayedCard(startingPlayer + i, playedCard);
            }
            if (playedCard.getColor() == atout) {
                if (currentPlie.color != atout) { // si on coupe
                    if (currentPlie.cut) { // already cut
                        switch (playedCard.getRank()) { // surcoupe
                            case Card.RANK_NELL:  // si on joue le nell
                                if (currentPlie.highest != Card.RANK_BOURG) {
                                    currentPlie.highest = playedCard.getRank();
                                    currentPlie.owner = (startingPlayer + i) % 4;
                                }
                                break;
                            case Card.RANK_BOURG:  // si on joue le bourg
                                currentPlie.highest = playedCard.getRank();
                                currentPlie.owner = (startingPlayer + i) % 4;
                                break;
                            default: // sinon
                                if (((currentPlie.highest != Card.RANK_BOURG) &&
                                        (currentPlie.highest != Card.RANK_NELL)) &&
                                        (playedCard.getRank() > currentPlie.highest)) {
                                    currentPlie.highest = playedCard.getRank();
                                    currentPlie.owner = (startingPlayer + i) % 4;
                                }
                                break;
                        }
                        // else souscoupe => nothing to do
                    } else {  // first to cut
                        currentPlie.cut = true;
                        currentPlie.highest = playedCard.getRank();
                        currentPlie.owner = (startingPlayer + i) % 4;
                    }
                } else {        // si c'est joué atout
                    switch (playedCard.getRank()) {
                        case Card.RANK_NELL: // si on joue le nell
                            if (currentPlie.highest != Card.RANK_BOURG) {
                                currentPlie.highest = playedCard.getRank();
                                currentPlie.owner = (startingPlayer + i) % 4;
                            }
                            break;
                        case Card.RANK_BOURG:  // si on joue le bourg
                            currentPlie.highest = playedCard.getRank();
                            currentPlie.owner = (startingPlayer + i) % 4;
                            break;
                        default: // sinon
                            if ((currentPlie.highest != Card.RANK_BOURG) && (currentPlie.highest != Card.RANK_NELL) && (playedCard.getRank() > currentPlie.highest)) {
                                currentPlie.highest = playedCard.getRank();
                                currentPlie.owner = (startingPlayer + i) % 4;
                            }
                            break;
                    }
                }
            } else if (playedCard.getColor() == currentPlie.color) {
                if ((playedCard.getRank() > currentPlie.highest) && !currentPlie.cut) {
                    currentPlie.owner = (startingPlayer + i) % 4;
                    currentPlie.highest = playedCard.getRank();
                }
            }
            currentPlie.score += nextAnswer[1]; // augmente le score de la plie
        }

        /* now everybody has played ... */

        /* waits a few seconds so that the players can see all the cards */
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }

        // communique qui a pris la plie
        for (var p : players) {
            p.sendPlieOwner(currentPlie.owner);
        }

        // choix et comptabilisation des annonces
        int playerWithStoeck = -1;
        int maxAnounce = 1;
        int maxHeight = 0;
        int anouncingTeam = -1;  // joueur qui a la plus grosse annonce
        for (var p : players) {
            for (int i = 0; i < p.getNbrAnounces(); i++) {
                var anounce = p.getAnouncement(i);
                System.out.println(p.getFirstName() + ", announce : " +
                        anounce.getType() + ", height : "
                        + anounce.getCard().getRank());
                if (anounce.getType() == Anouncement.STOECK) {
                    playerWithStoeck = p.getId();
                    continue;
                }
                if (anounce.getType() > maxAnounce) {
                    // plus grosse annonce
                    anouncingTeam = p.getTeam();
                    maxAnounce = anounce.getType();
                    maxHeight = anounce.getCard().getRank();
                } else if ((anounce.getType() == maxAnounce) &&
                        (anounce.getCard().getRank() > maxHeight)) {
                    // même annonce plus haute
                    anouncingTeam = p.getTeam();
                    maxHeight = anounce.getCard().getRank();
                } else if ((anounce.getType() == maxAnounce) &&
                        (anounce.getCard().getRank() == maxHeight) &&
                        (anounce.getCard().getColor() == atout)) {
                    // meme annonce, meme hauteur, mais atout
                    anouncingTeam = p.getTeam();
                }
            }
        }
        StringBuilder info;
        System.out.println("Bigger 'annonce' : " + anouncingTeam);

        if (anouncingTeam != -1) { // there are announces
            for (var p : players) {
                if ((p.getTeam() == anouncingTeam) && (p.getNbrAnounces() > 0)) {
                    // annonceur
                    info = new StringBuilder("20 " + p.getId() + " " + p.getNbrAnounces());
                    for (int j = 0; j < p.getNbrAnounces(); j++) {
                        var annoucement = p.getAnouncement(j);
                        int score = atout == Card.COLOR_SPADE ? 2 * annoucement.getValue() : annoucement.getValue();
                        teams[anouncingTeam].addScore(score);
                    }
                    for (var p2 : players) {
                        p2.sendAnouncementDetails(p.getId(), p.getAllAnouncements());
                    }
                } else if (p.getId() == playerWithStoeck) {
                    if (p.getTeam() == anouncingTeam) {
                        System.out.println("Error: " + p.getFirstName() + " declared only stock on first plie");
                    } else if (p.getNbrAnounces() > 1) {
                        System.out.println("Info: not counting stoeck for now for player " + p.getFirstName());
                    }
                }
                p.clearAnounces();
            }
        } else if (playerWithStoeck != -1) { // no announce but stock
            for (var p : players) {
                p.sendAnouncementDetails(playerWithStoeck, Collections.singletonList(new Anouncement(Anouncement.STOECK, null)));
            }
            int score = atout == Card.COLOR_SPADE ? Anouncement.VALUES[Anouncement.STOECK] * 2 : Anouncement.VALUES[Anouncement.STOECK];
            // add stock points
            var p = getPlayerById(playerWithStoeck);
            teams[p.getTeam()].addScore(score);
            p.clearAnounces();
        }

        // comptabilisation des points
        player = tableOrder.get(currentPlie.owner);
        teams[player.getTeam()].addScore(atout == Card.COLOR_SPADE ? currentPlie.score * 2 : currentPlie.score);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return -1;
        }

        return currentPlie.owner;
    }

    void processAnnouncements(Player player, int announcement) throws ClientLeftException {
        int[] instr;             // tableau d'instructions en integer
        switch (announcement) {
            case 1:        // Annonces
                System.out.println("Annonces");
                instr = player.requestAnoucementdetails(); // demande des précisions sur l'annonce
                for (int i = 0; i < instr[0]; i++)
                    player.addAnounce(instr[1 + 2 * i], new Card(instr[2 + 2 * i]));
                break;
            case 2:        // Stöck
                System.out.println("Annonces");
                player.addAnounce(0, null);
                break;
            case 3:        // Stöck + annonces
                System.out.println("Annonces");
                instr = player.requestAnoucementdetails();  // demande des précisions sur l'annonce
                for (int i = 0; i < instr[0]; i++)
                    player.addAnounce(instr[1 + 2 * i], new Card(instr[2 + 2 * i]));
                player.addAnounce(0, null);
                break;
        }
    }

    Player getPlayerById(int id) {
        for (Player p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
}
