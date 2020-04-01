//Title:        FlatJassServer
//Version:
//Copyright:    Copyright (c) 1998
//Author:       Pierre Métrailler & Jérome Berclaz
//Company:      Flat
//Description:  Your description

package com.leflat.jass.server;


import java.net.*;
import java.io.*;

public class ServerNetwork implements IServerNetwork {
    Socket myClientSocket = null;
    InputStreamReader isr;
    BufferedReader is;
    PrintWriter os;
    int clientId;


    public ServerNetwork() {
    }

    public boolean connect(ServerSocket myServerSocket) {
        // waiting and bind
        System.out.println("Waiting for connections");
        try {

            myClientSocket = myServerSocket.accept();

            //streams
            // a verifier le autoflush
            isr = new InputStreamReader(myClientSocket.getInputStream());
            is = new BufferedReader(isr);
            os = new PrintWriter(new BufferedOutputStream(myClientSocket.getOutputStream()), false);
        } catch (IOException e) {
            System.out.println("Error unable to bind socket");
            //System.exit(1);
        }
        System.out.println("Connection successful.");
        return true;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public void sendStr(String strToSend) {
        os.println(strToSend);
        os.flush();
        System.out.println("SERVER sent : " + strToSend);
    }

    public String rcvStr() throws ClientLeftException {
        String rcvTemp = null;

        //implementer timeout + exc

        try {
            rcvTemp = is.readLine();
        } catch (IOException e) {
            System.out.println("Error during reception");
            //System.exit(1);
        }
        if (rcvTemp != null)
            System.out.println("Received : " + rcvTemp);
        else {
            System.out.println("Client has left unexpectedly");
            throw new ClientLeftException(clientId);
        }

        return rcvTemp;
    }

    public void close() {
        try {
            myClientSocket.close();
        } catch (IOException e) {
            System.out.println("Error while closing socket");
            System.exit(1);
        }
    }
}
