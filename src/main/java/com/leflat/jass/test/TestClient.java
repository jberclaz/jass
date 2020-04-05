package com.leflat.jass.test;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.RemoteCommand;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class TestClient {
    public static final int PORT = 23107;

    private BufferedReader is;
    private PrintWriter os;

    TestClient() {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Started test server on port " + PORT);
            clientSocket = serverSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(clientSocket.getInputStream());
            is = new BufferedReader(isr);
            os = new PrintWriter(new BufferedOutputStream(clientSocket.getOutputStream()), false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String connectionMessage = receiveRawMessage();
        int gameId = Integer.parseInt(connectionMessage);
        sendMessage(String.valueOf(gameId));
        int playerId = 1;
        sendMessage(String.valueOf(playerId));
        String name = receiveMessage()[0];
        System.out.println("Client " + name + " connected");

        sendMessage(RemoteCommand.SET_PLAYER_INFO + " " + 0 + " Berte" );
        receiveMessage();

        sendMessage(RemoteCommand.SET_PLAYER_INFO + " " + 2 + " GC" );
        receiveMessage();

        sendMessage(RemoteCommand.SET_PLAYER_INFO + " " + 3 + " Pischus" );
        receiveMessage();

        sendMessage(String.valueOf(RemoteCommand.DRAW_CARD));

        int cardPosition = Integer.parseInt(receiveMessage()[0]);
        System.out.println("Drawn card: " +  cardPosition);

        var card = new Card(Card.RANK_BOURG, Card.COLOR_HEART);
        sendMessage(RemoteCommand.SET_CARD + " " + playerId + " " + cardPosition + " " +  card.getNumber());
        receiveMessage();

        var otherCard = new Card(Card.RANK_DAME, Card.COLOR_DIAMOND);
        sendMessage(RemoteCommand.SET_CARD + " " + (playerId+1) + " " + 20 + " " +  otherCard.getNumber());
        receiveMessage();

        sendMessage(RemoteCommand.SET_PLAYERS_ORDER + " 3 0 2 1");
        receiveMessage();

        sendMessage(String.valueOf(RemoteCommand.CHOOSE_PARTNER));
        int partnerId = Integer.parseInt(receiveMessage()[0]);

        sendMessage(RemoteCommand.SET_HAND + " 10 12 14 16 18");
        receiveMessage();

        sendMessage(String.valueOf(RemoteCommand.CHOOSE_ATOUT));
        int color = Integer.parseInt(receiveMessage()[0]);
        System.out.println("Chosen : " + color);

        sendMessage(String.valueOf(RemoteCommand.CHOOSE_ATOUT_SECOND));
        color = Integer.parseInt(receiveMessage()[0]);
        System.out.println("Chosen : " + color);

        sendMessage(RemoteCommand.SET_ATOUT + " " + color + " 2");
        receiveMessage();

        sendMessage(String.valueOf(RemoteCommand.PLAY));
        card = new Card(Integer.parseInt(receiveMessage()[0]));
        System.out.println(card);

        sendMessage(RemoteCommand.SET_PLAYED_CARD + " 0 35");
        receiveMessage();

        sendMessage(RemoteCommand.PLAY_NEXT + " 3 1 0");
        receiveMessage();
    }

    public static void main(String[] args) {
        new TestClient();
    }

    public String receiveRawMessage() {
        String message = null;

        try {
            message = is.readLine();
        } catch (IOException e) {
            System.out.println("Error during reception");
        }
        if (message != null)
            System.out.println("Received : " + message);
        else {
            System.out.println("Client has left unexpectedly");
        }
        return message;
    }

    public String[] receiveMessage() {
        var message = receiveRawMessage();
        var tokens = message.split(" ");
        if (tokens.length == 1) {
            return null;
        }
        return Arrays.copyOfRange(tokens, 1, tokens.length);
    }

      public int sendMessage(String message) {
        os.println(message);
        os.flush();
        System.out.println("Sent : " + message);  // DEBUG
        return 0;
    }

    public int sendMessage(List<String> message) {
        return sendMessage(String.join(" ", message));
    }
}
