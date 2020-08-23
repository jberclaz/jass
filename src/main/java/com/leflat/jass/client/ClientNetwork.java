package com.leflat.jass.client;

import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IClientNetwork;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientNetwork implements IClientNetwork {
    private final static Logger LOGGER = Logger.getLogger(ClientNetwork.class.getName());
    private static final int PORT_NUM = 23107;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    private Socket clientSocket;
    protected PrintWriter os;
    protected BufferedReader is;
    protected int playerId;

    @Override
    public ClientConnectionInfo connect(String host, int requestedGameId, String name) {
        try {
            clientSocket = new Socket();
            clientSocket.connect(new InetSocketAddress(host, PORT_NUM), CONNECTION_TIMEOUT_MS);

            is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            os = new PrintWriter(clientSocket.getOutputStream(), true);
            LOGGER.info("Connection to " + host + " successful");
        } catch (SocketTimeoutException e) {
            LOGGER.log(Level.WARNING, "Server did not answer", e);
            return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create socket: ", e);
            return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
        }

        sendRawMessage(String.valueOf(requestedGameId));

        try {
            int receivedGameId = Integer.parseInt(receiveRawMessage());
            if (receivedGameId < 0) {
                return new ClientConnectionInfo(receivedGameId);
            }
            playerId = Integer.parseInt(receiveRawMessage());
            var encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
            sendMessage(Collections.singletonList(encodedName));
            return new ClientConnectionInfo(playerId, receivedGameId, ConnectionError.CONNECTION_SUCCESSFUL);
        } catch (ServerDisconnectedException | UnsupportedEncodingException e) {
            LOGGER.log(Level.WARNING, "Server disconnected during connection", e);
        }
        return new ClientConnectionInfo(ConnectionError.SERVER_UNREACHABLE);
    }

    @Override
    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    @Override
    public void disconnect() {
        try {
            clientSocket.close();
            clientSocket = null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during disconnection", e);
        }
    }

    @Override
    public void sendMessage(List<String> message) {
        String stringMessage = playerId + " " + String.join(" ", message);
        sendRawMessage(stringMessage);
    }

    @Override
    public String receiveRawMessage() throws ServerDisconnectedException {
        String message = null;

        // TODO: implementer timeout + exc
        try {
            message = is.readLine();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during reception", e);
        }
        if (message == null) {
            throw new ServerDisconnectedException("Server has left unexpectedly");
        }
        LOGGER.info("Received : " + message);

        return message;
    }

    private void sendRawMessage(String message) {
        os.println(message);
        LOGGER.info("Sent : " + message);
    }
}
