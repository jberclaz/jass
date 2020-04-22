package com.leflat.jass.test;

import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.client.ModernUiFactory;
import com.leflat.jass.client.OriginalUiFactory;
import com.leflat.jass.common.Card;

import java.util.Arrays;

import static com.leflat.jass.test.TestClient.buildHand;

public class TestPlayerUi {
    public static void main(String[] aregs) {
        var clientNetworkFactory = new MockNetworkFactory();
        var player = new JassPlayer(clientNetworkFactory, new ModernUiFactory());
        player.setName("me");

        while (!player.isConnected()) {
            waitSec(1);
        }
        var player1 =new ClientPlayer(1, "GC");
        var player2 = new ClientPlayer(2, "Berte");
        var player3 = new ClientPlayer(3, "Pischus");
        player.setPlayerInfo(player1);
        player.setPlayerInfo(player2);
        player.setPlayerInfo(player3);

        player.prepareTeamDrawing(true);

        player.setCard(player3, 10, new Card(Card.RANK_ROI, Card.COLOR_DIAMOND));

        waitSec(2);

        player.setHand(buildHand(1, 2, 3, 4, 5, 6, 7, 8, 9));

        player.setAtout(Card.COLOR_HEART, player1);

        player.play();

        player.setPlayedCard(player2, new Card(Card.RANK_BOURG, Card.COLOR_HEART));

        for (int i=0; i<9; i++) {
            var card = player.play();
            System.out.println(card);
        }

    }

    static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
