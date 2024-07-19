package com.leflat.jass.test;

import com.leflat.jass.client.*;
import com.leflat.jass.common.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestClient {
    class TestPlayer implements IConnectable {

        @Override
        public int connect(String name, String host, int gameId) {
            return 0;
        }

        @Override
        public boolean disconnect() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return false;
        }
    }

    TestClient() {
        var uiFactory = new ModernUiFactory();
        var player = new TestPlayer();
        var frame = uiFactory.getUi(player);

        frame.showUi(true);

        try {
            frame.setPlayer(new ClientPlayer(0, "JB"), 0);
            frame.setPlayer(new ClientPlayer(1, "Berte"), 1);
            frame.setPlayer(new ClientPlayer(2, "GC"), 2);
            frame.setPlayer(new ClientPlayer(3, "Pischus"), 3);
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame.setPlayerHand(buildHand(1, 2, 3, 4, 5, 6, 7, 8, 9));
        frame.setOtherPlayersHands(9);
        frame.setPlayedCard(new Card(Card.COLOR_HEART, Card.RANK_BOURG), 0);
        frame.setPlayedCard(new Card(Card.COLOR_HEART, Card.RANK_DAME), 1);
        frame.setPlayedCard(new Card(Card.COLOR_HEART, Card.RANK_10), 2);
        frame.setPlayedCard(new Card(Card.COLOR_HEART, Card.RANK_6), 3);
        frame.collectPlie(2);
        frame.setPlayedCard(new Card(Card.COLOR_SPADE, Card.RANK_BOURG), 2);
/*
        frame.prepareGame();




        waitSec(2);

        frame.collectPlie(3);

        frame.setAtout(Card.COLOR_HEART, 0);
        frame.setScore(157, 257);
        */

    }

    void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        new TestClient();
    }

    public static List<Card> buildHand(int... numbers) {
        return Arrays.stream(numbers).mapToObj(Card::new).collect(Collectors.toList());
    }
}
