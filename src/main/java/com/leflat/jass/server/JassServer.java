package com.leflat.jass.server;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;


public class JassServer {
    public static final int DEFAULT_PORT = 23107;

    public static void main(String[] args) {
        System.out.println("FLAT Jass System Server");
        System.out.println("Version 1.2");
        System.out.println("(c) 2000-2020 by FLAT(r)");
        System.out.println();

        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);
        try {
            for (var handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            var handler = new FileHandler("jass_server-%u.%g.log");
            handler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(handler);
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
