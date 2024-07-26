package com.leflat.jass.server;

import com.leflat.jass.common.IServerNetwork;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServerNetwork implements IServerNetwork {
    protected BufferedReader is;
    protected PrintWriter os;
    protected int playerId = -1;
    private final static Logger LOGGER = Logger.getLogger(GameController.class.getName());

    public ServerNetwork(Socket socket) throws IOException {
        is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        os = new PrintWriter(socket.getOutputStream(), true);
    }

    protected ServerNetwork() {

    }

    @Override
    public void setPlayerId(int id) {
        playerId = id;
    }

    @Override
    public void sendMessage(String... message) {
        String rawMessage = String.join(" ", message);
        os.println(rawMessage);
        LOGGER.info("Sent to " + playerId + " : " + rawMessage);
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
            LOGGER.log(Level.SEVERE, "Error during reception", e);
        }
        if (message == null) {
            os.close();
            throw new PlayerLeftExpection(playerId);
        }
        LOGGER.info("Received from " + playerId + " : " + message);
        return message;
    }
}
