package com.leflat.jass.server;

import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Plie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.leflat.jass.server.RulesTests.buildHand;
import static org.junit.jupiter.api.Assertions.*;

public class ArtificialPlayerTests {
    ArtificialPlayer player;
    Field playersPositionField, gameViewField, currentPlieField, handField;
    ArtificialPlayer otherPlayer;
    Map<Integer, Integer> playersPosition;
    GameView gameView;
    Plie currentPlie;
    List<Card> hand;

    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        player = new ArtificialPlayer(1, "name");
        playersPositionField = ArtificialPlayer.class.getDeclaredField("playersPositions");
        playersPositionField.setAccessible(true);
        gameViewField = ArtificialPlayer.class.getDeclaredField("gameView");
        gameViewField.setAccessible(true);
        currentPlieField = ArtificialPlayer.class.getDeclaredField("currentPlie");
        currentPlieField.setAccessible(true);
        handField = BasePlayer.class.getDeclaredField("hand");
        handField.setAccessible(true);
        playersPosition = (Map<Integer, Integer>) playersPositionField.get(player);
        gameView = (GameView) gameViewField.get(player);
        hand = (List<Card>) handField.get(player);
        otherPlayer = new ArtificialPlayer(2, "Pischus");
    }

    @Test
    public void test_set_player_info() {
        player.setPlayerInfo(otherPlayer);
        assertEquals(playersPosition.size(), 1);
        assertTrue(playersPosition.containsKey(2));
        assertEquals(playersPosition.get(2), 1);
    }

    @Test
    public void test_draw_card() {
        for (int i = 0; i < 35; i++) {
            player.setCard(otherPlayer, i, new Card(0));
        }
        var card = player.drawCard();
        assertEquals(card, 35);
    }

    @Test
    public void test_player_order() throws IllegalAccessException {
        var thirdPlayer = new ArtificialPlayer(3, "GC");
        var fourthPlayer = new ArtificialPlayer(0, "Mono");
        player.setPlayerInfo(otherPlayer);
        player.setPlayerInfo(thirdPlayer);
        player.setPlayerInfo(fourthPlayer);
        assertEquals(playersPosition.size(), 3);
        assertEquals(playersPosition.get(0), 3);

        player.setPlayersOrder(Arrays.asList(3, 2, 0, 1));
        assertEquals(playersPosition.get(3), 1);
        assertEquals(playersPosition.get(2), 2);
        assertEquals(playersPosition.get(0), 3);
    }

    @Test
    public void test_set_hand() throws PlayerLeftExpection, IllegalAccessException {
        player.setHand(buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33));
        assertEquals(hand.size(), 9);
        assertTrue(hand.contains(new Card(9)));
        currentPlie = (Plie) currentPlieField.get(player);
        assertEquals(currentPlie.getSize(), 0);
    }

    @Test
    public void test_set_atout() throws PlayerLeftExpection {
        player.setHand(buildHand(1, 2, 10, 11, 20, 21, 30, 31, 32));
        assertEquals(player.chooseAtout(true), Card.COLOR_NONE);
        assertEquals(player.chooseAtout(false), Card.COLOR_DIAMOND);
        player.setHand(buildHand(1, 2, 3, 4, 10, 20, 30, 31, 32));
        assertEquals(player.chooseAtout(true), Card.COLOR_SPADE);
    }

    @Test
    public void test_played_card() throws PlayerLeftExpection, IllegalAccessException {
        Card.atout = Card.COLOR_DIAMOND;
        player.setPlayerInfo(otherPlayer);
        player.setHand(buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33));
        player.setPlayedCard(otherPlayer, new Card(2));
        currentPlie = (Plie) currentPlieField.get(player);
        assertEquals(currentPlie.getSize(), 1);
        player.setPlayedCard(otherPlayer, new Card(11));

    }

    @Test
    public void test_play() throws PlayerLeftExpection {
        var originalHand = buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33);
        player.setHand(originalHand);
        var card = player.play();
        assertEquals(8, hand.size());
        assertFalse(hand.contains(card));
        assertTrue(originalHand.contains(card));
    }

    @Test
    public void test_get_announcements() throws PlayerLeftExpection {
        player.setHand(buildHand(1, 2, 3, 9, 10, 11, 20, 21, 23));
        player.setAtout(Card.COLOR_HEART, otherPlayer);
        player.play();
        assertEquals(8, hand.size());
        var an = player.getAnnouncements();
        assertEquals(2, an.size());
        assertTrue(an.contains(new Announcement(Announcement.THREE_CARDS, new Card(3))));
        assertTrue(an.contains(new Announcement(Announcement.THREE_CARDS, new Card(11))));
        player.play();
        an = player.getAnnouncements();
        assertEquals(0, an.size());
    }

    @Test
    public void test_set_announcements() throws PlayerLeftExpection, NoSuchFieldException, IllegalAccessException {
        player.setPlayerInfo(otherPlayer);
        player.setHand(buildHand(1, 2, 3, 9, 10, 11, 20, 21, 23));
        player.setAnnouncements(otherPlayer, Collections.singletonList(new Announcement(Announcement.THREE_CARDS, new Card(33))));

        Field cardsInGameField = GameView.class.getDeclaredField("cardsInGame");
        Field cardsInHandsField = GameView.class.getDeclaredField("cardsInHands");
        cardsInGameField.setAccessible(true);
        cardsInHandsField.setAccessible(true);
        var cardsInGame = (Map<Integer, Float[]>) cardsInGameField.get(gameView);
        var cardsInHands = (Map<Integer, List<Card>>) cardsInHandsField.get(gameView);
        assertEquals(24, cardsInGame.size());
        assertEquals(3, cardsInHands.get(0).size());
        assertTrue(cardsInHands.get(0).contains(new Card(33)));
        assertTrue(cardsInHands.get(0).contains(new Card(32)));
        assertTrue(cardsInHands.get(0).contains(new Card(31)));
        assertEquals(0, cardsInGame.get(34)[0]);
        assertEquals(0.5f, cardsInGame.get(34)[1]);
        assertEquals(0, cardsInGame.get(30)[0]);
    }
}
