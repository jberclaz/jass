package com.leflat.jass.client;

import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IClientNetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;

public class ClientNetwork implements IClientNetwork {
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

    @Override
    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    @Override
    public void disconnect() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
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
            System.out.println("Error during reception");
        }
        if (message == null) {
            throw new ServerDisconnectedException("Server has left unexpectedly");
        }
        System.out.println("Received : " + message);

        return message;
    }

    private void sendRawMessage(String message) {
        os.println(message);
        os.flush();
        System.out.println("Envoi au serveur : " + message);
    }
}
