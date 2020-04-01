/*
 * ClientListener.java
 *
 * Created on 18. avril 2000, 18:15
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version 1.2
 */

package com.leflat.jass.client;

import java.io.*;
import java.net.*;

public class ClientListener extends Thread{
    FlatJassClientSystem app;
    Socket threadSocket;
    InputStreamReader isr;
    BufferedReader is;
    public boolean stop = false;

    public ClientListener(FlatJassClientSystem app, Socket cs) {
	this.app = app;
	threadSocket=cs;
	stop = false;
	try {
	    isr = new InputStreamReader(threadSocket.getInputStream());
	    is = new BufferedReader(isr);
	}
	catch (IOException e) {
	    System.out.println("LISTENER:Impossible stream");
	}
    }

/*  public ClientListener(FlatJassClientSystem app) {
    this.app = app;
    }*/

    public void setSocket(Socket cs) {
	threadSocket = cs;
	try {
	    isr = new InputStreamReader(threadSocket.getInputStream());
	    is = new BufferedReader(isr);
	}
	catch (IOException e) {
	    System.out.println("LISTENER:Impossible stream");
	}
    }

    public void run() {
	/*  int c;
	    StringBuffer s = new StringBuffer(50);
	    String answer;
	    int len;
	    System.out.println("Client Listener : prêt");
	    try {
	    while (true) {
	    s.setLength(0);
	    try {
	    while ((c = System.in.read()) != '\n')
            s.append((char)c);
	    }
	    catch (IOException e) {
	    }
	    answer = s.toString();
	    len = answer.length();
	    app.execute(answer.substring(0, len-1));
	    //  app.execute(answer);
	    }
	    }
	    catch (Exception e) {
	    }*/
	System.out.println("Starting Listener...");

	while (!stop) {
	    String rcvTemp = null;

	    //implementer timeout + exc

	    try {
		rcvTemp=is.readLine();
		System.out.println("LISTENER:Reception:"+rcvTemp);
		app.execute(rcvTemp);
	    }
	    catch (IOException e) {
		System.out.println("LISTENER:Erreur reception");
		//System.exit(1);
	    }
	}

	//this.stop();
    }
}
