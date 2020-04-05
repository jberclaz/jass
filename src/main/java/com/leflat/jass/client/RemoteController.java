package com.leflat.jass.client;

import com.leflat.jass.common.*;
import com.leflat.jass.server.PlayerLeftExpection;
import jdk.jshell.spi.ExecutionControl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RemoteController extends Thread {
    private static final int PORT_NUM = 23107;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private boolean running = false;
    private int playerId;
    private IPlayer player;
    private Socket clientSocket = new Socket();
    private PrintWriter os;
    private BufferedReader is;

    public RemoteController(IPlayer player) {
        this.player = player;
    }

    public ClientConnectionInfo connect(String host, int requestedGameId, String name) {
        try {
            clientSocket.connect(new InetSocketAddress(host, PORT_NUM), CONNECTION_TIMEOUT_MS);

            os = new PrintWriter(clientSocket.getOutputStream(), false);
            is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            System.out.println("Connection successful");
        } catch (SocketTimeoutException e) {
            System.out.println("Server does not answer");
            return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
        } catch (IOException e) {
            System.out.println("Unable to create socket: " + e);
            return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
        }

        sendRawMessage(String.valueOf(requestedGameId));

        try {
            int receivedGameId = Integer.parseInt(receiveRawMessage());
            if (receivedGameId < 0) {
                return new ClientConnectionInfo(receivedGameId);
            }
            playerId = Integer.parseInt(receiveRawMessage());
            sendMessage(Collections.singletonList(name));
            return new ClientConnectionInfo(playerId, receivedGameId, ConnectionError.CONNECTION_SUCCESSFUL);
        } catch (ServerDisconnectedException e) {
            e.printStackTrace();
        }
        return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected();
    }

    public void sendRawMessage(String message) {
        os.println(message);
        os.flush();
        System.out.println("Envoi au serveur : " + message);
    }

    public void sendMessage(List<String> message) {
        String stringMessage = playerId + " " + String.join(" ", message);
        sendRawMessage(stringMessage);
    }

    public String receiveRawMessage() throws ServerDisconnectedException {
        String message = null;

        // TODO: implementer timeout + exc
        try {
            message = is.readLine();
        } catch (IOException e) {
            System.out.println("Error during reception");
        }
        if (message != null)
            System.out.println("Received : " + message);
        else {
            System.out.println("Server has left unexpectedly");
            throw new ServerDisconnectedException();
        }
        return message;
    }

    public void disconnect() {
        try {
            running = false;
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Error while closing socket");
            //System.exit(1);
        }
    }

    @Override
    public void run() {
        running = true;
        System.out.println("Starting Listener...");
        while (running) {
            try {
                String message = receiveRawMessage();
                try {
                    handleControllerMessage(message.split(" "));
                } catch (ExecutionControl.NotImplementedException e) {
                    e.printStackTrace();
                }
            } catch (ServerDisconnectedException e) {
                e.printStackTrace();
                running = false;
                // TODO: player.serverDisconnected
            }
        }
        System.out.println("Exiting listener");
    }

    private void handleControllerMessage(String[] message) throws ExecutionControl.NotImplementedException {
        int command = Integer.parseInt(message[0]);
        List<String> answer = Collections.emptyList();
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
                int atout = Integer.parseInt(message[1]);
                var firstToPlay = new ClientPlayer(Integer.parseInt(message[2]));
                try {
                    player.setAtout(atout, firstToPlay);
                } catch (PlayerLeftExpection playerLeftExpection) {
                    playerLeftExpection.printStackTrace();
                    return;
                }
                break;
            case RemoteCommand.PLAY:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.SET_PLAYED_CARD:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.PLAY_NEXT:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.SET_PLIE_OWNER:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.SET_SCORES:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.GET_ANOUNCEMENTS:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.SET_ANOUNCEMENTS:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.SET_GAME_RESULT:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.GET_NEW_GAME:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            case RemoteCommand.PLAYER_LEFT:
                throw new ExecutionControl.NotImplementedException("Not implemented");
            default:
                System.err.println("Unknown command " + command);
        }
        sendMessage(answer);
    }
}