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
    private ArrayList<Player> players = new ArrayList<>(); // les 4 joueurs
    private Team[] teams = new Team[2];       // les 2 équipes
    private ArrayList<Player> tableOrder = new ArrayList<>();

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
                for (Player player : players) {
                    if (player != disconnectedPlayer) {
                        player.sendMessage("23 " + disconnectedPlayer.getId());
                    }
                }
                players.remove(disconnectedPlayer);
                continue;
            }

            try {
                boolean playMore;
                do {
                    chooseTeam();     // détermine les équipes

                    // Play one game (until 1500)
                    playPart();

                    // ask whether they want to play another part
                    players.get(0).sendMessage("22");

                    String[] instr = decode(players.get(0).waitForAnswer()); // réponse
                    playMore = Integer.parseInt(instr[1]) != 0;

                    for (Team team : teams) {
                        team.resetScore();
                    }
                } while (playMore);

            } catch (ClientLeftException e) {
                Player disconnectedPlayer = getPlayerById(e.getClientId());
                for (Player player : players) {
                    if (player != disconnectedPlayer) {
                        player.sendMessage("23 " + disconnectedPlayer.getId());
                    }
                }
                players.remove(disconnectedPlayer);
            }
        } while (true); //  loop forever
    }

    // attend la connexion d'un joueur
    public Player waitForConnection() throws ClientLeftException {
        ServerNetwork clientConnection = new ServerNetwork();
        if (clientConnection.connect(myServerSocket)) {
            // la connexion a réussi
            clientConnection.setClientId(lastPlayerId);
            clientConnection.sendStr("1 " + lastPlayerId); // donne son id et demande des infos
            String[] instr = decode(clientConnection.rcvStr());  // attend les infos

            Player newPlayer = new Player(instr[1], instr[2], lastPlayerId, clientConnection);
            System.out.println(instr[1] + " " + instr[2] + " is connected");

            for (Player player : players) {
                // infos SUR LES joueurs déjà connectés
                newPlayer.sendMessage("2 " + player.getId() + " " + player.getFirstName() + " " + player.getLastName());
                newPlayer.waitForAnswer(); // attend la réponse

                // infos AUX joueurs déjà connectés
                player.sendMessage("2 " + newPlayer.getId() + " " + newPlayer.getFirstName() + " " + newPlayer.getLastName());
                player.waitForAnswer(); // attend la réponse
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


    // Procédure de décodage des instructions
    private String[] decode(String instr) {
        int cmpt = 0;
        int cursor = 0;
        String[] table = new String[10];
        for (int i = 1; i < instr.length(); i++)
            if (instr.charAt(i) == ' ') {
                table[cmpt] = instr.substring(cursor, i);
                cursor = i + 1;
                cmpt++;
            }
        table[cmpt] = instr.substring(cursor);
        return table;
    }


    // Procédure de décodage des instructions en integer
    private int[] decodint(String instr) {
        int cmpt = 0;
        int cursor = 0;
        int temp;
        int[] table = new int[10];
        for (int i = 1; i < instr.length(); i++)
            if (instr.charAt(i) == ' ') {
                temp = Integer.parseInt(instr.substring(cursor, i));
                table[cmpt] = temp;
                cursor = i + 1;
                cmpt++;
            }
        temp = Integer.parseInt(instr.substring(cursor));
        table[cmpt] = temp;
        return table;
    }


    private void chooseTeam() throws ClientLeftException {
        for (Team t : teams) {
            t.reset();
        }
        players.get(0).sendMessage("3");    // demande de choisir le mode de tirage des équipes
        String[] instr = decode(players.get(0).waitForAnswer()); // réponse
        int teamChoiceMethod = Integer.parseInt(instr[1]);
        if (teamChoiceMethod == 1) { // choisir au hasard
            sendMessageToAllPlayers("4");  // préparation du tirage des équipes

            boolean drawingSuccessful;
            HashMap<Player, Integer> cardsChoosen = new HashMap<>();    // cartes tirées
            do {
                int[] cards = shuffleCards();
                for (Player p : players) {
                    p.sendMessage("5");  // demande de choisir une carte
                    instr = decode(p.waitForAnswer());  //réponse
                    int cartNumber = Integer.parseInt(instr[1]);

                    cardsChoosen.put(p, cards[cartNumber]);
                    sendMessageToAllPlayers("6 " + p.getId() + " " + cartNumber + " " + cardsChoosen.get(p));
                }
                // delay to allow players to watch cards
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }

                // détermine les équipes
                drawingSuccessful = calculateTeam(cardsChoosen);
                if (!drawingSuccessful) {
                    sendMessageToAllPlayers("7");
                }
            } while (!drawingSuccessful);
        } else {       // choisir son partenaire
            players.get(0).sendMessage("9");    // demande de choisir le partenaire
            instr = decode(players.get(0).waitForAnswer()); // réponse
            int partnerId = Integer.parseInt(instr[1]);
            for (Player p : players) {
                if (p.getId() == partnerId || p == players.get(0)) {
                    p.setTeam(0);
                    teams[0].addPlayer(p);
                } else {
                    p.setTeam(1);
                    teams[1].addPlayer(p);
                }
            }
        }
        organisePlayers();

        String playerOrder = "8 " + tableOrder.stream().map(Player::getId).map(String::valueOf).collect(Collectors.joining(" "));
        sendMessageToAllPlayers(playerOrder);
    }


    // organise les joueurs et les systemnetworks
    void organisePlayers() {
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


    void playPart() throws ClientLeftException {
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
                    p.sendMessage("18 " + teams[p.getTeam()].getScore()
                            + " " + teams[(p.getTeam() + 1) % 2].getScore());
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
                    p.sendMessage("18 " + teams[p.getTeam()].getScore()
                            + " " + teams[(p.getTeam() + 1) % 2].getScore());
                }
            }

            // répète jusqu'à ce qu'on gagne
        } while (!teams[0].hasWon() && !teams[1].hasWon());

        // Sends the winner to all player
        int winner = teams[0].hasWon() ? 0 : 1;
        sendMessageToAllPlayers("21 " + winner + " " +
                teams[winner].getPlayer(0).getId() + " " +
                teams[winner].getPlayer(1).getId());

        /* waits a few seconds so that the players can see all the cards */
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
        }
    }


    int chooseAtout(int playerNumber) throws ClientLeftException {
        Player first = tableOrder.get(playerNumber);
        first.sendMessage("11");   // demande de faire atout en premier
        String[] instr = decode(first.waitForAnswer());  // réponse
        var choice = Integer.parseInt(instr[1]);
        if (choice == 4) {     // si on passe
            var second = tableOrder.get((playerNumber + 2) % 4);
            second.sendMessage("12");   // demande de faire atout en second
            instr = decode(second.waitForAnswer());  // réponse
        }
        choice = Integer.parseInt(instr[1]);
        sendMessageToAllPlayers("13 " + choice + " " + playerNumber);
        return choice;
    }


    int drawCards() throws ClientLeftException {
        int playerWithDiamondSeven = 0;      // 7 de carreau
        int[] cards = shuffleCards();     // choisir les cartes au hasard
        StringBuilder s;                  // chaîne à envoyer
        for (int i = 0; i < 4; i++) {
            s = new StringBuilder("10");
            for (int j = 0; j < 9; j++) {
                s.append(" ").append(cards[i * 9 + j]);
                if (cards[i * 9 + j] == Card.DIAMOND_SEVEN) {    // 7 de carreau
                    playerWithDiamondSeven = i;
                }
            }
            tableOrder.get(i).sendMessage(s.toString());
            tableOrder.get(i).waitForAnswer();
        }
        return playerWithDiamondSeven;
    }


    int playPlie(int startingPlayer) throws ClientLeftException {
        var player = tableOrder.get(startingPlayer);
        player.sendMessage("14");    // demande de jouer en premier
        String[] instr = decode(player.waitForAnswer()); //réponse
        var card = new Card(Integer.parseInt(instr[1]));
        currentPlie.color = card.getColor();
        currentPlie.highest = card.getRank();
        currentPlie.score = Integer.parseInt(instr[2]);
        currentPlie.owner = startingPlayer;
        currentPlie.cut = false;

        int announcement = Integer.parseInt(instr[3]); // Annonces ?
        handleAnnoucements(player, announcement);

        for (int i = 1; i < 4; i++) {           // envoie la carte jouée aux autres
            tableOrder.get((startingPlayer + i) % 4).sendMessage("15 " + startingPlayer + " " + instr[1]);
            tableOrder.get((startingPlayer + i) % 4).waitForAnswer();
        }

        for (int i = 1; i < 4; i++) {     // demande de jouer
            player = tableOrder.get((startingPlayer + i) % 4);
            player.sendMessage("16 " + currentPlie.highest + " " + currentPlie.color + " " + (currentPlie.cut ? 1 : 0));
            instr = decode(player.waitForAnswer()); //réponse
            var playedCard = new Card(Integer.parseInt(instr[1]));

            announcement = Integer.parseInt(instr[3]); // Annonces ?
            handleAnnoucements(player, announcement);

            for (int j = 1; j < 4; j++) {             // envoie la carte jouée aux autres
                tableOrder.get((startingPlayer + i + j) % 4).sendMessage("15 " + (startingPlayer + i) + " " + instr[1]);
                tableOrder.get((startingPlayer + i + j) % 4).waitForAnswer();
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
            currentPlie.score += Integer.parseInt(instr[2]); // augmente le score de la plie
        }

        /* now everybody has played ... */

        /* waits a few seconds so that the players can see all the cards */
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }

        // communique qui a pris la plie
        sendMessageToAllPlayers("17 " + currentPlie.owner);

        // choix et comptabilisation des annonces
        int stoeck = -1;
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
                    stoeck = p.getId();
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
                        info.append(" ").append(annoucement.getType()).append(" ").append(annoucement.getCard());
                        int score = atout == Card.COLOR_SPADE ? 2 * annoucement.getValue() : annoucement.getValue();
                        teams[anouncingTeam].addScore(score);
                    }
                    sendMessageToAllPlayers(info.toString());
                } else if (p.getId() == stoeck) {
                    if (p.getTeam() == anouncingTeam) {
                        System.out.println("Error: " + p.getFirstName() + " declared only stock on first plie");
                    } else if (p.getNbrAnounces() > 1) {
                        System.out.println("Info: not counting stoeck for now for player " + p.getFirstName());
                    }
                }
                p.clearAnounces();
            }
        } else if (stoeck != -1) { // no announce but stock
            info = new StringBuilder("20 " + stoeck + " 1 0 0");
            sendMessageToAllPlayers(info.toString());
            int score = atout == Card.COLOR_SPADE ? Anouncement.VALUES[Anouncement.STOECK] * 2 : Anouncement.VALUES[Anouncement.STOECK];
            // add stock points
            var p = getPlayerById(stoeck);
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

    void handleAnnoucements(Player player, int announcement) throws ClientLeftException {
        int[] instr;             // tableau d'instructions en integer
        switch (announcement) {
            case 1:        // Annonces
                System.out.println("Annonces");
                player.sendMessage("19");  // demande des précisions sur l'annonce
                instr = decodint(player.waitForAnswer()); //réponse
                for (int i = 0; i < instr[1]; i++)
                    player.addAnounce(instr[2 + 2 * i], new Card(instr[3 + 2 * i]));
                break;
            case 2:        // Stöck
                System.out.println("Annonces");
                player.addAnounce(0, null);
                break;
            case 3:        // Stöck + annonces
                System.out.println("Annonces");
                player.sendMessage("19");  // demande des précisions sur l'annonce
                instr = decodint(player.waitForAnswer()); //réponse
                for (int i = 0; i < instr[1]; i++)
                    player.addAnounce(instr[2 + 2 * i], new Card(instr[3 + 2 * i]));
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

    void sendMessageToAllPlayers(String message) throws ClientLeftException {
        for (Player player : players) {
            player.sendMessage(message);
            player.waitForAnswer();
        }
    }
}


//**************** Autres Classes **********************************************


class Player {
    // Variables
    private String firstName;
    private String lastName;
    private int id;
    private int team;
    private ArrayList<Anouncement> anounces = new ArrayList<>(); // annonces
    private ServerNetwork connection;

    // Constructeur
    public Player(String firstName, String lastName, int id, ServerNetwork connection) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.connection = connection;
    }

    protected void finalize() {
        if (connection != null) {
            connection.close();
        }
    }

    // Méthodes
    public void clearAnounces() {
        anounces.clear();
    }

    public void addAnounce(int type, Card card) {
        anounces.add(new Anouncement(type, card));
    }

    public void sendMessage(String message) {
        connection.sendStr(message);
    }

    public String waitForAnswer() throws ClientLeftException {
        return connection.rcvStr();
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public int getNbrAnounces() {
        return anounces.size();
    }

    public Anouncement getAnouncement(int i) {
        return anounces.get(i);
    }
}

class Team {
    // Variables
    private int currentScore;
    private ArrayList<Player> players = new ArrayList<>();

    // Constructeur
    public Team() {
        currentScore = 0;
    }

    // Méthodes
    public void resetScore() {
        currentScore = 0;
    }

    public void reset() {
        players.clear();
        resetScore();
    }

    public void addScore(int score) {
        currentScore += score;
    }

    public boolean hasWon() {
        return currentScore > 1499;
    }

    public int getScore() {
        return currentScore;
    }

    public Player getPlayer(int i) {
        return players.get(i);
    }

    public void addPlayer(Player p) {
        players.add(p);
    }
}

