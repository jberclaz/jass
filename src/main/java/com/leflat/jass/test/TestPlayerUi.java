package com.leflat.jass.test;

import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.client.ModernUi;

public class TestPlayerUi {
    public static void main(String[] aregs) {
        System.setProperty("sun.java2d.opengl", "True");

        var ui = new ModernUi();
        ui.showUi(true);

        ui.setPlayer(new ClientPlayer(0, "GC"), 0);
        ui.setPlayer(new ClientPlayer(1, "Pischus"), 1);
        ui.setPlayer(new ClientPlayer(2, "Pischus"), 2);
        ui.setPlayer(new ClientPlayer(3, "Pischus"), 3);

        ui.prepareGame();
    }

    static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
