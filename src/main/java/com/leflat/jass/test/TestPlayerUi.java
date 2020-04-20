package com.leflat.jass.test;

import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.client.OriginalUiFactory;
import com.leflat.jass.common.Team;

import java.util.Arrays;

public class TestPlayerUi {
    public static void main(String[] aregs) {
        var clientNetworkFactory = new MockNetworkFactory();
        var player = new JassPlayer(clientNetworkFactory, new OriginalUiFactory());
        player.setName("me");

        while (!player.isConnected()) {
            waitSec(1);
        }

        player.setPlayerInfo(new ClientPlayer(1, "GC"));
        player.setPlayerInfo(new ClientPlayer(2, "Berte"));
        player.setPlayerInfo(new ClientPlayer(3, "Pischus"));

        waitSec(1);
        player.setPlayersOrder(Arrays.asList(3, 2, 1, 0));

        waitSec(1);
        player.setPlayersOrder(Arrays.asList(0, 1, 2, 3));

        waitSec(1);
        player.setPlayersOrder(Arrays.asList(1, 3, 0, 2));

        waitSec(1);
        player.setPlayersOrder(Arrays.asList(2, 1, 3, 0));

        waitSec(1);

        var team = new Team(0);
        team.addPlayer(new ClientPlayer(0));
        team.addPlayer(new ClientPlayer(2));
        player.setHandScore(0, 257, team);
    }

    static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
