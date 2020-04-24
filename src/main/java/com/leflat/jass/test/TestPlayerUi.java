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
        System.setProperty("sun.java2d.opengl", "True");
        var clientNetworkFactory = new MockNetworkFactory();
        var player = new JassPlayer(clientNetworkFactory, new ModernUiFactory());
        player.setName("me");

        while (!player.isConnected()) {
            waitSec(1);
        }
        var player1 = new ClientPlayer(1, "GC");
        var player2 = new ClientPlayer(2, "Berte");
        var player3 = new ClientPlayer(3, "Pischus");

        //waitSec(1);
        player.setPlayerInfo(player1);
        //waitSec(1);
        player.setPlayerInfo(player2);
        //waitSec(1);
        player.setPlayerInfo(player3);
        //waitSec(1);
        //player.chooseTeamSelectionMethod();

        //player.prepareTeamDrawing(true);
/*
        int cardNumber = player.drawCard();
        player.setCard(player, cardNumber, new Card(Card.RANK_NELL, Card.COLOR_CLUB));
//        waitSec(1);
        player.setCard(player1, 10, new Card(Card.RANK_DAME, Card.COLOR_DIAMOND));
  //      waitSec(1);
        player.setCard(player2, 20, new Card(Card.RANK_10, Card.COLOR_HEART));
    //    waitSec(1);
        player.setCard(player3, 30, new Card(Card.RANK_ROI, Card.COLOR_SPADE));

      //  player.setPlayersOrder(Arrays.asList(1, 3, 2, 0));
*/
        player.setHand(buildHand(1, 2, 3, 4, 5, 6, 7, 8, 9));

        //var atout = player.chooseAtout(true);

        player.setAtout(Card.COLOR_HEART, player);

        //player.play();

        //waitSec(1);
        player.setPlayedCard(player1, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player3, new Card(Card.RANK_6, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player2, new Card(Card.RANK_8, Card.COLOR_CLUB));

        player.play();

        player.collectPlie(player1);

        player.play();

        //waitSec(1);
        player.setPlayedCard(player1, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player3, new Card(Card.RANK_6, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player2, new Card(Card.RANK_8, Card.COLOR_CLUB));

        player.collectPlie(player2);

        player.play();


        //waitSec(1);
        player.setPlayedCard(player1, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player3, new Card(Card.RANK_6, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player2, new Card(Card.RANK_8, Card.COLOR_CLUB));

        player.collectPlie(player3);

        waitSec(1);

        player.setPlayedCard(player, new Card(Card.RANK_BOURG, Card.COLOR_SPADE));

        //waitSec(1);
        player.setPlayedCard(player1, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player3, new Card(Card.RANK_6, Card.COLOR_DIAMOND));

        //waitSec(1);
        player.setPlayedCard(player2, new Card(Card.RANK_8, Card.COLOR_CLUB));

        waitSec(5);
        player.collectPlie(player);

        /*

        player.setPlayedCard(player1, new Card(Card.RANK_BOURG, Card.COLOR_HEART));
        player.setPlayedCard(player2, new Card(Card.RANK_DAME, Card.COLOR_HEART));
        player.setPlayedCard(player3, new Card(Card.RANK_NELL, Card.COLOR_HEART));

        player.play();

        player.collectPlie(player1);


        for (int i = 0; i < 9; i++) {
            var card = player.play();
            System.out.println(card);
        }
*/
    }

    static void waitSec(float seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
