package com.leflat.jass.server;

import com.leflat.jass.common.IServerNetwork;

import java.io.*;
import java.util.Arrays;

public class ServerInMemoryNetwork implements IServerNetwork {
    protected BufferedReader is;
    protected PrintWriter os;
    protected int playerId = -1;

    public ServerInMemoryNetwork(PipedInputStream input, PipedOutputStream output) {
        this.is = new BufferedReader(new InputStreamReader(input));
        this.os = new PrintWriter(output);
    }

    @Override
    public void sendMessage(String... message) {
        String rawMessage = String.join(" ", message);
        os.println(rawMessage);
    }

    @Override
    public void setPlayerId(int id) {
        playerId = id;
    }

    @Override
    public String receiveRawMessage() throws PlayerLeftExpection {
        String message = null;
        // TODO: implementer timeout + exc
        try {
            message = is.readLine();
        } catch (IOException e) {

        }
        if (message == null) {
            throw new PlayerLeftExpection(playerId);
        }
        return message;
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
}
