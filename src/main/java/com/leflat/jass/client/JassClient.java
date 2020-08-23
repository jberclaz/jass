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
        var uiFactory = args.length > 0 && args[0].compareTo("original") == 0 ? new OriginalUiFactory() : new ModernUiFactory();
        new JassPlayer(new ClientNetworkFactory(), uiFactory);
    }
}
