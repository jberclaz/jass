package com.leflat.jass.client;

import java.io.IOException;
import java.util.logging.*;

public class JassClient {
    public static void main(String[] args) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);
        try {
            for (var handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            var handler = new FileHandler("jass_client-%u.%g.log");
            handler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.setProperty("sun.java2d.opengl", "true");
        new JassPlayer(new ClientNetworkFactory(), new ModernUiFactory());
    }
}
