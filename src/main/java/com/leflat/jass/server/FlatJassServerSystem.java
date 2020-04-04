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
/*
public class FlatJassServerSystem {
    private int atout;
    private int lastPlayerId;    // nombre de joueurs connectés
    private Plie currentPlie;        // plie en cours
    private List<BasePlayer> players = new ArrayList<>(); // les 4 joueurs
    private Team[] teams = new Team[2];       // les 2 équipes
    private List<BasePlayer> tableOrder = new ArrayList<>();

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
                    BasePlayer newPlayer = waitForConnection();
                    players.add(newPlayer);
                }
            } catch (PlayerLeftExpection e) {
                BasePlayer disconnectedPlayer = getPlayerById(e.getPlayerId());
                players.remove(disconnectedPlayer);
                for (BasePlayer player : players) {
                    try {
                        player.sendPlayerLeft(disconnectedPlayer.getId());
                    } catch (PlayerLeftExpection ee) {
                        System.err.println("Player " + ee.getPlayerId() + " also left.");
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

            } catch (PlayerLeftExpection e) {
                BasePlayer disconnectedPlayer = getPlayerById(e.getPlayerId());
                players.remove(disconnectedPlayer);
                for (var player : players) {
                    try {
                        player.sendPlayerLeft(disconnectedPlayer.getId());
                    } catch (PlayerLeftExpection ee) {
                        System.err.println("Player " + ee.getPlayerId() + " also left.");
                    }
                }
            }
        } while (true); //  loop forever
    }

    // attend la connexion d'un joueur
    public BasePlayer waitForConnection() throws PlayerLeftExpection {
        var clientConnection = new ServerNetwork();
        if (clientConnection.connect(myServerSocket)) {
            // la connexion a réussi
            clientConnection.setClientId(lastPlayerId);

            var newPlayer = new BasePlayer(lastPlayerId, clientConnection);
            System.out.println(newPlayer.getName() + " " + newPlayer.getLastName() + " is connected");

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

    /*
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

     */
/*

    private void chooseTeam() throws PlayerLeftExpection {
        Arrays.stream(teams).forEach(Team::reset);

        int teamChoiceMethod = players.get(0).chooseTeamSelectionMethod();
        if (teamChoiceMethod == 1) { // choisir au hasard
            chooseTeamsRandomly();
        } else {       // choisir son partenaire
            pickTeamMates();
        }

        reorderPlayers();

        var idOrder = tableOrder.stream().map(BasePlayer::getId).collect(Collectors.toList());
        for (var p : players) {
            p.sendPlayerOrder(idOrder);
        }
    }

    void chooseTeamsRandomly() throws PlayerLeftExpection {
        // préparation du tirage des équipes
        for (BasePlayer player : players) {
            player.prepareTeamChoice();
        }

        boolean drawingSuccessful;
        do {
            HashMap<BasePlayer, Integer> cardsDrawn = new HashMap<>();    // cartes tirées
            int[] cards = shuffleCards();
            for (BasePlayer p : players) {
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

    // organise les joueurs et les systemnetworks
    void reorderPlayers() {
        tableOrder.clear();
        tableOrder.add(teams[0].getPlayer(0));
        tableOrder.add(teams[1].getPlayer(0));
        tableOrder.add(teams[0].getPlayer(1));
        tableOrder.add(teams[1].getPlayer(1));
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


    void playOneGame() throws PlayerLeftExpection {
        /* randomly choose the cards and send them */
        // celui qui commence la partie et fait atout

/*
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

/*
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

/*
        try {
            Thread.sleep(4000);
        } catch (InterruptedException ignored) {
        }
    }


    int chooseAtout(int playerNumber) throws PlayerLeftExpection {
        BasePlayer first = tableOrder.get(playerNumber);
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


    int drawCards() throws PlayerLeftExpection {
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

    void processCard(Card playedCard, int playerIdx, int score) {
        if (playedCard.getColor() == atout) {
            if (currentPlie.color != atout) { // si on coupe
                if (currentPlie.cut) { // already cut
                    switch (playedCard.getRank()) { // surcoupe
                        case Card.RANK_NELL:  // si on joue le nell
                            if (currentPlie.highest != Card.RANK_BOURG) {
                                currentPlie.highest = playedCard.getRank();
                                currentPlie.owner = playerIdx;
                            }
                            break;
                        case Card.RANK_BOURG:  // si on joue le bourg
                            currentPlie.highest = playedCard.getRank();
                            currentPlie.owner = playerIdx;
                            break;
                        default: // sinon
                            if (((currentPlie.highest != Card.RANK_BOURG) &&
                                    (currentPlie.highest != Card.RANK_NELL)) &&
                                    (playedCard.getRank() > currentPlie.highest)) {
                                currentPlie.highest = playedCard.getRank();
                                currentPlie.owner = playerIdx;
                            }
                            break;
                    }
                    // else souscoupe => nothing to do
                } else {  // first to cut
                    currentPlie.cut = true;
                    currentPlie.highest = playedCard.getRank();
                    currentPlie.owner = playerIdx;
                }
            } else {        // si c'est joué atout
                switch (playedCard.getRank()) {
                    case Card.RANK_NELL: // si on joue le nell
                        if (currentPlie.highest != Card.RANK_BOURG) {
                            currentPlie.highest = playedCard.getRank();
                            currentPlie.owner = playerIdx;
                        }
                        break;
                    case Card.RANK_BOURG:  // si on joue le bourg
                        currentPlie.highest = playedCard.getRank();
                        currentPlie.owner = playerIdx;
                        break;
                    default: // sinon
                        if ((currentPlie.highest != Card.RANK_BOURG) && (currentPlie.highest != Card.RANK_NELL) && (playedCard.getRank() > currentPlie.highest)) {
                            currentPlie.highest = playedCard.getRank();
                            currentPlie.owner = playerIdx;
                        }
                        break;
                }
            }
        } else if (playedCard.getColor() == currentPlie.color) {
            if ((playedCard.getRank() > currentPlie.highest) && !currentPlie.cut) {
                currentPlie.owner = playerIdx;
                currentPlie.highest = playedCard.getRank();
            }
        }
        currentPlie.score += score; // augmente le score de la plie
    }

    void handlAllAnnoucements() throws PlayerLeftExpection {
        int playerWithStoeck = -1;
        int highestAnnouncement = 1;
        int maxRank = Card.RANK_6;
        int anouncingTeam = -1;  // joueur qui a la plus grosse annonce
        for (var p : players) {
            for (var anouncement : p.getAnouncements()) {
                System.out.println(p.getName() + ", announce : " +
                        anouncement.toString());
                if (anouncement.getType() == Anouncement.STOECK) {
                    playerWithStoeck = p.getId();
                    continue;
                }
                if (anouncement.getType() > highestAnnouncement) {
                    // plus grosse annonce
                    anouncingTeam = p.getTeam();
                    highestAnnouncement = anouncement.getType();
                    maxRank = anouncement.getCard().getRank();
                } else if ((anouncement.getType() == highestAnnouncement) &&
                        (anouncement.getCard().getRank() > maxRank)) {
                    // même annonce plus haute
                    anouncingTeam = p.getTeam();
                    maxRank = anouncement.getCard().getRank();
                } else if ((anouncement.getType() == highestAnnouncement) &&
                        (anouncement.getCard().getRank() == maxRank) &&
                        (anouncement.getCard().getColor() == atout)) {
                    // meme annonce, meme hauteur, mais atout
                    anouncingTeam = p.getTeam();
                }
            }
        }

        if (anouncingTeam >= 0) { // there are announces
            for (var p : players) {
                if ((p.getTeam() == anouncingTeam) && (p.getNbrAnounces() > 0)) {
                    // annonceur
                    teams[anouncingTeam].addAnnoucementScore(p.getAnouncements(), atout);
                    for (var p2 : players) {
                        p2.sendAnouncementDetails(p.getId(), p.getAnouncements());
                    }
                } else if (p.getId() == playerWithStoeck) {
                    if (p.getTeam() == anouncingTeam) {
                        System.out.println("Error: " + p.getName() + " declared only stock on first plie");
                    } else if (p.getNbrAnounces() > 1) {
                        System.out.println("Info: not counting stoeck for now for player " + p.getName());
                    }
                }
                p.clearAnouncement();
            }
        } else if (playerWithStoeck != -1) { // no announce but stock
            for (var p : players) {
                p.sendAnouncementDetails(playerWithStoeck, Collections.singletonList(new Anouncement(Anouncement.STOECK, null)));
            }
            int score = atout == Card.COLOR_SPADE ? Anouncement.VALUES[Anouncement.STOECK] * 2 : Anouncement.VALUES[Anouncement.STOECK];
            // add stock points
            var p = getPlayerById(playerWithStoeck);
            teams[p.getTeam()].addScore(score);
            p.clearAnouncement();
        }
    }

    int playPlie(int startingPlayer) throws PlayerLeftExpection {
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
            processCard(playedCard, (startingPlayer + i) % 4, nextAnswer[1]);
        }

        /* now everybody has played ... */

        /* waits a few seconds so that the players can see all the cards */
/*
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }

        // communique qui a pris la plie
        for (var p : players) {
            p.sendPlieOwner(currentPlie.owner);
        }

        // choix et comptabilisation des annonces
        handlAllAnnoucements();

        // comptabilisation des points
        player = tableOrder.get(currentPlie.owner);
        teams[player.getTeam()].addScore(atout == Card.COLOR_SPADE ? currentPlie.score * 2 : currentPlie.score);

        if (teams[0].hasWon() || teams[1].hasWon()) {
            return -1;
        }

        return currentPlie.owner;
    }

    void processAnnouncements(BasePlayer player, int announcement) throws PlayerLeftExpection {
        int[] instr;             // tableau d'instructions en integer
        switch (announcement) {
            case 1:        // Annonces
                System.out.println("Annonces");
                instr = player.requestAnoucementDetails(); // demande des précisions sur l'annonce
                for (int i = 0; i < instr[0]; i++)
                    player.addAnouncement(instr[1 + 2 * i], new Card(instr[2 + 2 * i]));
                break;
            case 2:        // Stöck
                System.out.println("Annonces");
                player.addAnouncement(0, null);
                break;
            case 3:        // Stöck + annonces
                System.out.println("Annonces");
                instr = player.requestAnoucementDetails();  // demande des précisions sur l'annonce
                for (int i = 0; i < instr[0]; i++)
                    player.addAnouncement(instr[1 + 2 * i], new Card(instr[2 + 2 * i]));
                player.addAnouncement(0, null);
                break;
        }
    }

    BasePlayer getPlayerById(int id) {
        for (BasePlayer p : players) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
}
*/