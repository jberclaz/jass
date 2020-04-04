/*
 * ClientListener.java
 *
 * Created on 18. avril 2000, 18:15
 */


/**
 * @author Berclaz Jérôme
 * @version 1.2
 */

package com.leflat.jass.client;

import java.io.*;
import java.net.*;

public class ClientListener extends Thread {
    FlatJassClientSystem app;
    Socket threadSocket;
    InputStreamReader isr;
    BufferedReader is;
    public boolean stop = false;

    public ClientListener(FlatJassClientSystem app, Socket cs) {
        this.app = app;
        threadSocket = cs;
        stop = false;
        try {
            isr = new InputStreamReader(threadSocket.getInputStream());
            is = new BufferedReader(isr);
        } catch (IOException e) {
            System.out.println("LISTENER:Impossible stream");
        }
    }


    public void setSocket(Socket cs) {
        threadSocket = cs;
        try {
            isr = new InputStreamReader(threadSocket.getInputStream());
            is = new BufferedReader(isr);
        } catch (IOException e) {
            System.out.println("LISTENER:Impossible stream");
        }
    }

    public void run() {
        System.out.println("Starting Listener...");

        while (!stop) {
            String rcvTemp = null;

            //implementer timeout + exc

            try {
                rcvTemp = is.readLine();
                System.out.println("LISTENER:Reception:" + rcvTemp);
                app.execute(rcvTemp);
            } catch (IOException e) {
                System.out.println("LISTENER:Erreur reception");
                //System.exit(1);
            }
        }
    }
}
