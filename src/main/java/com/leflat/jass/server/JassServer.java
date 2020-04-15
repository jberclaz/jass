package com.leflat.jass.server;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JassServer {
    private final static Logger LOGGER = Logger.getLogger(GameController.class.getName());
    public static final int DEFAULT_PORT = 23107;

    public static void main(String[] args) {
        System.out.println("FLAT Jass System Server");
        System.out.println("Version 1.2");
        System.out.println("(c) 2000-2020 by FLAT(r)");
        System.out.println();

        LOGGER.setLevel(Level.INFO);

        try {
            for (var handler : LOGGER.getHandlers()) {
                LOGGER.removeHandler(handler);
            }
            LOGGER.addHandler(new FileHandler("jass_server-%u.%g.log"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // TODO: add command line argument parsing (Apache commons)
        ConnectionListener listener = null;

        try {
            listener = new ConnectionListener(DEFAULT_PORT);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        listener.start();
    }
}
