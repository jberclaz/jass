package com.leflat.jass.test;

import com.leflat.jass.client.ClientNetworkFactory;
import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.client.OriginalUiFactory;
import com.leflat.jass.common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        var uiFactory = new OriginalUiFactory();
        var player = new TestPlayer();
        var frame = uiFactory.getUi(player);

        List<BasePlayer> players = new ArrayList<>();
        players.add(new ClientPlayer(2, "Berte"));
        players.add(new ClientPlayer(1, "Hhip"));
        players.add(new ClientPlayer(9, "JB"));
        players.add(new ClientPlayer(4, "Mono"));

        frame.showUi(true);

        try {
            frame.setPlayer(players.get(0), 0);
            frame.setPlayer(players.get(1), 1);
            frame.setPlayer(players.get(2), 2);
            frame.setPlayer(players.get(3), 3);
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame.setAtout(1, 0);
        waitSec(1);

        frame.setAtout(1, 1);
        waitSec(1);

        frame.setAtout(1, 2);
        waitSec(1);

        frame.setAtout(1, 3);
        waitSec(1);

        int atout = frame.chooseAtout(false);
        System.out.println("atout " + atout);

        var team = new Team(0);
        team.addPlayer(new ClientPlayer(0, "Hhip"));
        team.addPlayer(new ClientPlayer(1, "Mono"));
        var choice = frame.choosePartner(players);
        System.out.println("partner: " + choice);

        frame.displayMatch(team, false);

        frame.displayGameResult(team, false);
    }

    public static void main(String[] args) {

            new TestClient();

    }

      static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
