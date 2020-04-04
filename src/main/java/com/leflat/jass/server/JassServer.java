package com.leflat.jass.server;

import java.io.IOException;

public class JassServer {
    public static final int DEFAULT_PORT = 23107;

    public static void main(String[] args) {
        System.out.println("FLAT Jass System Server");
        System.out.println("Version 1.2");
        System.out.println("(c) 2000-2020 by FLAT(r)");
        System.out.println();

        // TODO: add command line argument parsing (Apache commons)
        NetworkListener listener = null;

        try {
            listener = new NetworkListener(DEFAULT_PORT);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        listener.start();
    }
}
