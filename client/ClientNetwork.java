/*
 * ClientNetwork.java
 *
 * Created on 18. avril 2000, 18:17
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version 1.2
 */

import java.net.*;
import java.io.*;

public class ClientNetwork {

    public static final int PORT_NUM=32107;
    Socket myClientSocket=null;

    PrintWriter os;

    public ClientNetwork() {

    }

    public Socket connect(String ipAddress) {

	//streams
	try {
	    myClientSocket = new Socket(ipAddress, PORT_NUM);

	    os = new PrintWriter(myClientSocket.getOutputStream(),false);
	    System.out.println("Connection successful");
	}
	catch (IOException e) {
	    System.out.println("Unable to create socket");
            System.out.println(e);
	    //System.exit(1);
            return null;
	}

	return this.myClientSocket;
    }

    public void sendTo(String msg) {
	os.println(msg);
	os.flush();
	System.out.println("Envoi au serveur : " + msg);
    }

    public void disconnect() {
	try {
	    myClientSocket.close();
	}
	catch (IOException e) {
	    System.out.println("Error while closing socket");
	    //System.exit(1);
	}
    }
}
