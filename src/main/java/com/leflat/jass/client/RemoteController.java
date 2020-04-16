package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RemoteController implements IController, Runnable {
    private final static Logger LOGGER = Logger.getLogger(RemoteController.class.getName());
    private boolean running = false;
    private IPlayer player;
    private Lock lock;
    private IClientNetwork network;

    public RemoteController(IPlayer player, IClientNetwork network) {
        this.player = player;
        this.network = network;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void run() {
        lock = new ReentrantLock();
        running = true;
        LOGGER.info("Starting Listener...");
        while (running) {
            try {
                String message = network.receiveRawMessage();
                handleControllerMessage(message.split(" "));
            } catch (ServerDisconnectedException e) {
                LOGGER.log(Level.WARNING, "Server disconnected", e);
                if (running) {
                    player.lostServerConnection();
                    running = false;
                }
            }
        }
        LOGGER.info("Exiting listener ");
    }

    @Override
    public void terminate() {
        running = false;
    }

    private void handleControllerMessage(String[] message) {
        int command = Integer.parseInt(message[0]);
        List<String> answer = new ArrayList<>();
        try {
            switch (command) {
                case RemoteCommand.SET_PLAYER_INFO:
                    int playerId = Integer.parseInt(message[1]);
                    String name = message[2];
                    player.setPlayerInfo(new ClientPlayer(playerId, name));
                    break;
                case RemoteCommand.CHOOSE_TEAM_SELECTION_METHOD:
                    var choice = player.chooseTeamSelectionMethod();
                    answer = Collections.singletonList(String.valueOf(choice));
                    break;
                case RemoteCommand.PREPARE_TEAM_DRAWING:
                    player.prepareTeamDrawing(true);
                    break;
                case RemoteCommand.DRAW_CARD:
                    int position = player.drawCard();
                    answer = Collections.singletonList(String.valueOf(position));
                    break;
                case RemoteCommand.SET_CARD:
                    int pId = Integer.parseInt(message[1]);
                    int cardPosition = Integer.parseInt(message[2]);
                    int cardNumber = Integer.parseInt(message[3]);
                    player.setCard(new ClientPlayer(pId), cardPosition, new Card(cardNumber));
                    break;
                case RemoteCommand.RESTART_TEAM_DRAWING:
                    player.prepareTeamDrawing(false);
                    break;
                case RemoteCommand.CHOOSE_PARTNER:
                    int partnerId = player.choosePartner();
                    answer = Collections.singletonList(String.valueOf(partnerId));
                    break;
                case RemoteCommand.SET_PLAYERS_ORDER:
                    var order = Arrays.stream(message).skip(1).map(Integer::parseInt).collect(Collectors.toList());
                    player.setPlayersOrder(order);
                    break;
                case RemoteCommand.SET_HAND:
                    var hand = Arrays.stream(message).skip(1).map(Integer::parseInt).map(Card::new).collect(Collectors.toList());
                    player.setHand(hand);
                    break;
                case RemoteCommand.CHOOSE_ATOUT:
                case RemoteCommand.CHOOSE_ATOUT_SECOND:
                    int atout = player.chooseAtout(command == RemoteCommand.CHOOSE_ATOUT);
                    answer = Collections.singletonList(String.valueOf(atout));
                    break;
                case RemoteCommand.SET_ATOUT:
                    atout = Integer.parseInt(message[1]);
                    var firstToPlay = new ClientPlayer(Integer.parseInt(message[2]));
                    player.setAtout(atout, firstToPlay);
                    break;
                case RemoteCommand.PLAY:
                    var card = player.play();
                    answer = Collections.singletonList(String.valueOf(card.getNumber()));
                    break;
                case RemoteCommand.SET_PLAYED_CARD:
                    var player = new ClientPlayer(Integer.parseInt(message[1]));
                    card = new Card(Integer.parseInt(message[2]));
                    this.player.setPlayedCard(player, card);
                    break;
                case RemoteCommand.COLLECT_PLIE:
                    player = new ClientPlayer(Integer.parseInt(message[1]));
                    this.player.collectPlie(player);
                    break;
                case RemoteCommand.SET_SCORES:
                    int ourScore = Integer.parseInt(message[1]);
                    int opponentScore = Integer.parseInt(message[2]);
                    this.player.setScores(ourScore, opponentScore);
                    break;
                case RemoteCommand.GET_ANOUNCEMENTS:
                    var anouncements = this.player.getAnnouncements();
                    answer.add(String.valueOf(anouncements.size()));
                    for (var an : anouncements) {
                        answer.add(String.valueOf(an.getType()));
                        answer.add(String.valueOf(an.getCard().getNumber()));
                    }
                    break;
                case RemoteCommand.SET_ANOUNCEMENTS:
                    player = new ClientPlayer(Integer.parseInt(message[1]));
                    int numberAnoucements = Integer.parseInt(message[2]);
                    anouncements = new ArrayList<>();
                    for (int i = 0; i < numberAnoucements; i++) {
                        var c = new Card(Integer.parseInt(message[4 + i * 2]));
                        anouncements.add(new Announcement(Integer.parseInt(message[3 + i * 2]), c));
                    }
                    this.player.setAnnouncements(player, anouncements);
                    break;
                case RemoteCommand.SET_GAME_RESULT:
                    var winningTeam = new Team(Integer.parseInt(message[1]));
                    winningTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[2])));
                    winningTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[3])));
                    this.player.setGameResult(winningTeam);
                    break;
                case RemoteCommand.GET_NEW_GAME:
                    boolean newGame = this.player.getNewGame();
                    answer = Collections.singletonList(newGame ? "1" : "0");
                    break;
                case RemoteCommand.PLAYER_LEFT:
                    player = new ClientPlayer(Integer.parseInt(message[1]));
                    this.player.playerLeft(player);
                    break;
                case RemoteCommand.SET_MATCH:
                    var matchTeam = new Team(Integer.parseInt(message[1]));
                    matchTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[2])));
                    matchTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[3])));
                    this.player.setMatch(matchTeam);
                default:
                    LOGGER.warning("Unknown command " + command);
            }
        } catch (PlayerLeftExpection playerLeftExpection) {
            LOGGER.log(Level.WARNING, "Lost connection to server", playerLeftExpection);
            return;
        }
        network.sendMessage(answer);
    }
}
