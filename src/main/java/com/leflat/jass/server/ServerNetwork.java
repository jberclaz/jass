package com.leflat.jass.server;

import com.leflat.jass.common.IServerNetwork;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ServerNetwork implements IServerNetwork {
    private BufferedReader is;
    private PrintWriter os;
    private int playerId = -1;

    public ServerNetwork(Socket socket) throws IOException {
        var isr = new InputStreamReader(socket.getInputStream());
        is = new BufferedReader(isr);
        os = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), false);
    }

    @Override
    public void setPlayerId(int id) {
        playerId = id;
    }

    @Override
    public void sendMessage(String... message) {
        String rawMessage = String.join(" ", message);
        os.println(rawMessage);
        os.flush();
        System.out.println("Sent : " + rawMessage);  // DEBUG
    }

    @Override
    public String[] receiveMessage() throws PlayerLeftExpection {
        var message = receiveRawMessage();
        var tokens = message.split(" ");
        assert Integer.parseInt(tokens[0]) == playerId;
        if (tokens.length == 1) {
            return null;
        }
        return Arrays.copyOfRange(tokens, 1, tokens.length);
    }

    public String receiveRawMessage() throws PlayerLeftExpection {
        String message = null;
        // TODO: implementer timeout + exc
        try {
            message = is.readLine();
        } catch (IOException e) {
            System.out.println("Error during reception");
        }
        if (message == null) {
            throw new PlayerLeftExpection(playerId);
        }
        System.out.println("Received : " + message); // DEBUG
        return message;
    }
}
