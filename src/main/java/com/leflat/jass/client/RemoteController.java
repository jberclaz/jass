package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RemoteController implements IController, Runnable {
    private boolean running = false;
    private IPlayer player;
    private Lock lock;
    private IClientNetwork network;

    public RemoteController(IPlayer player, IClientNetwork network) {
        this.player = player; this.network = network;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void run() {
        lock = new ReentrantLock();
        running = true;
        System.out.println("Starting Listener...");
        while (running) {
            try {
                String message = network.receiveRawMessage();
                handleControllerMessage(message.split(" "));
            } catch (ServerDisconnectedException e) {
                e.printStackTrace();
                running = false;
                // TODO: player.serverDisconnected
            }
        }
        System.out.println("Exiting listener ");
    }

    @Override
    public void terminate() {
        running = false;
    }

    private void handleControllerMessage(String[] message) {
        int command = Integer.parseInt(message[0]);
        List<String> answer = new ArrayList<>();
        switch (command) {
            case RemoteCommand.SET_PLAYER_INFO:
                int playerId = Integer.parseInt(message[1]);
                String name = message[2];
                try {
                    player.setPlayerInfo(new ClientPlayer(playerId, name));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.CHOOSE_TEAM_SELECTION_METHOD:
                try {
                    var choice = player.chooseTeamSelectionMethod();
                    answer = Collections.singletonList(String.valueOf(choice));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.PREPARE_TEAM_DRAWING:
                try {
                    player.prepareTeamDrawing(true);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.DRAW_CARD:
                try {
                    int position = player.drawCard();
                    answer = Collections.singletonList(String.valueOf(position));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_CARD:
                int pId = Integer.parseInt(message[1]);
                int cardPosition = Integer.parseInt(message[2]);
                int cardNumber = Integer.parseInt(message[3]);
                try {
                    player.setCard(new ClientPlayer(pId), cardPosition, new Card(cardNumber));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.RESTART_TEAM_DRAWING:
                try {
                    player.prepareTeamDrawing(false);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.CHOOSE_PARTNER:
                try {
                    int partnerId = player.choosePartner();
                    answer = Collections.singletonList(String.valueOf(partnerId));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_PLAYERS_ORDER:
                var order = Arrays.stream(message).skip(1).map(Integer::parseInt).collect(Collectors.toList());
                try {
                    player.setPlayersOrder(order);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_HAND:
                var hand = Arrays.stream(message).skip(1).map(Integer::parseInt).map(Card::new).collect(Collectors.toList());
                try {
                    player.setHand(hand);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.CHOOSE_ATOUT:
            case RemoteCommand.CHOOSE_ATOUT_SECOND:
                try {
                    int atout = player.chooseAtout(command == RemoteCommand.CHOOSE_ATOUT);
                    answer = Collections.singletonList(String.valueOf(atout));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_ATOUT:
                try {
                    int atout = Integer.parseInt(message[1]);
                    var firstToPlay = new ClientPlayer(Integer.parseInt(message[2]));
                    player.setAtout(atout, firstToPlay);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.PLAY:
                try {
                    var card = player.play();
                    answer = Collections.singletonList(String.valueOf(card.getNumber()));
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_PLAYED_CARD:
                try {
                    var p = new ClientPlayer(Integer.parseInt(message[1]));
                    var card = new Card(Integer.parseInt(message[2]));
                    player.setPlayedCard(p, card);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.COLLECT_PLIE:
                try {
                    var pl = new ClientPlayer(Integer.parseInt(message[1]));
                    player.collectPlie(pl);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_SCORES:
                try {
                    int ourScore = Integer.parseInt(message[1]);
                    int opponentScore = Integer.parseInt(message[2]);
                    player.setScores(ourScore, opponentScore);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
            case RemoteCommand.GET_ANOUNCEMENTS:
                try {
                    var anouncements = player.getAnoucement();
                    answer.add(String.valueOf(anouncements.size()));
                    for (var an : anouncements) {
                        answer.add(String.valueOf(an.getType()));
                        answer.add(String.valueOf(an.getCard().getNumber()));
                    }
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_ANOUNCEMENTS:
                try {
                    var p = new ClientPlayer(Integer.parseInt(message[1]));
                    int numberAnoucements = Integer.parseInt(message[2]);
                    List<Anouncement> anouncements = new ArrayList<>();
                    for (int i = 0; i < numberAnoucements; i++) {
                        var c = new Card(Integer.parseInt(message[4 + i * 2]));
                        anouncements.add(new Anouncement(Integer.parseInt(message[3 + i * 2]), c));
                    }
                    player.setAnouncement(p, anouncements);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.SET_GAME_RESULT:
                try {
                    var winningTeam = new Team(Integer.parseInt(message[1]));
                    winningTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[2])));
                    winningTeam.addPlayer(new ClientPlayer(Integer.parseInt(message[3])));
                    player.setGameResult(winningTeam);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.GET_NEW_GAME:
                try {
                    boolean newGame = player.getNewGame();
                    answer = Collections.singletonList(newGame ? "1" : "0");
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.PLAYER_LEFT:
                try {
                    var p = new ClientPlayer(Integer.parseInt(message[1]));
                    player.playerLeft(p);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            default:
                System.err.println("Unknown command " + command);
        }
        network.sendMessage(answer);
    }
}
