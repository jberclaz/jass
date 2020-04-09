package com.leflat.jass.client;

public class JassClient {
    public static void main(String[] args) {
        new JassPlayer(new RemoteControllerFactory(), new OriginalUiFactory());
    }
}
