package com.leflat.jass.test;

import com.leflat.jass.client.*;
import com.leflat.jass.common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TestClient {
    class TestPlayer implements IRemotePlayer {

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
        var team = new Team(0);
        team.addPlayer(new ClientPlayer(0, "Hhip"));
        team.addPlayer(new ClientPlayer(1, "Mono"));
        frame.displayGameResult(team, true);
        List<BasePlayer> players = new ArrayList<>();
        players.add(new ClientPlayer(0, "Berte"));
        players.add(new ClientPlayer(0, "Hhip"));
        players.add(new ClientPlayer(0, "JB"));

        frame.choosePartner(players);

        frame.prepareGame();
        frame.setPlayedCard(new Card(Card.RANK_ROI, Card.COLOR_HEART), 0);
        frame.setPlayedCard(new Card(Card.RANK_ROI, Card.COLOR_DIAMOND), 1);
        frame.setPlayedCard(new Card(Card.RANK_ROI, Card.COLOR_SPADE), 2);
        frame.setPlayedCard(new Card(Card.RANK_ROI, Card.COLOR_CLUB), 3);

        try {
            frame.setPlayer(new ClientPlayer(0, "JB"), 0);
            frame.setPlayer(new ClientPlayer(1, "Berte"), 1);
            frame.setPlayer(new ClientPlayer(2, "GC"), 2);
            frame.setPlayer(new ClientPlayer(3, "Pischus"), 3);
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame.setPlayerHand(buildHand(1, 2, 3, 4, 5, 6, 7, 8, 9));
        frame.setOtherPlayersHands(8);
    }

    public static void main(String[] args) {
        new TestClient();
    }

      public static List<Card> buildHand(int... numbers) {
        return Arrays.stream(numbers).mapToObj(Card::new).collect(Collectors.toList());
    }
}
