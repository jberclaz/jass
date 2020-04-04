package com.leflat.jass.server;

import java.io.IOException;

public class JassServer {
    public static void main(String[] args) {
        System.out.println("FLAT Jass System Server");
        System.out.println("Version 1.2");
        System.out.println("(c) 2000-2020 by FLAT(r)");
        System.out.println();

        // TODO: add command line argument parsing (Apache commons)
        int port = 23107;
        NetworkListener listener = null;

        try {
            listener = new NetworkListener(port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        listener.start();
    }
}
