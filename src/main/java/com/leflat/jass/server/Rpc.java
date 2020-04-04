package com.leflat.jass.server;

import com.leflat.jass.common.PlayerLeftExpection;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Rpc {
    private BufferedReader is;
    private PrintWriter os;
    private int playerId = -1;

    public Rpc(Socket socket) throws IOException {
        var isr = new InputStreamReader(socket.getInputStream());
        is = new BufferedReader(isr);
        os = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), false);
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

    public String receiveRawMessage() throws PlayerLeftExpection {
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
            System.out.println("Client has left unexpectedly");
            throw new PlayerLeftExpection(playerId);
        }
        return message;
    }

    public String[] receiveMessage() throws PlayerLeftExpection {
        var message = receiveRawMessage();
        var tokens = message.split(" ");
        assert Integer.parseInt(tokens[0]) == playerId;
        if (tokens.length == 1) {
            return null;
        }
        return Arrays.copyOfRange(tokens, 1, tokens.length);
    }

    void setPlayerId(int id) {
        playerId = id;
    }
}
