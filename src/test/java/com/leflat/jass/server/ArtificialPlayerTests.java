package com.leflat.jass.server;

import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Plie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

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
    Map<Integer, Float[]> unknownCardsInGame;
    List<Card>[] knownCardsInHands;
    Field playedCardField;


    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        player = new ArtificialPlayer(1, "name");
        playersPositionField = ArtificialPlayer.class.getDeclaredField("positionsByIds");
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
        Field unknownCardsInGameField = GameView.class.getDeclaredField("unknownCardsInGame");
        Field knownCardsInHandsField = GameView.class.getDeclaredField("knownCardsInHands");
        unknownCardsInGameField.setAccessible(true);
        knownCardsInHandsField.setAccessible(true);
        unknownCardsInGame = (Map<Integer, Float[]>) unknownCardsInGameField.get(gameView);
        knownCardsInHands = (List<Card>[]) knownCardsInHandsField.get(gameView);
        playedCardField = ArtificialPlayer.class.getDeclaredField("playedCard");
        playedCardField.setAccessible(true);
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
        player.prepareTeamDrawing(true);

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
    public void test_choose_atout() throws PlayerLeftExpection {
        var thirdPlayer = new ArtificialPlayer(3, "GC");
        var fourthPlayer = new ArtificialPlayer(0, "Mono");
        player.setPlayerInfo(otherPlayer);
        player.setPlayerInfo(thirdPlayer);
        player.setPlayerInfo(fourthPlayer);
        player.setPlayersOrder(Arrays.asList(0, 1, 2, 3));
        player.setHand(buildHand(1, 2, 10, 11, 20, 21, 29, 31, 28));
        assertEquals(player.chooseAtout(true), Card.COLOR_NONE);
        assertEquals(player.chooseAtout(false), Card.COLOR_CLUB);
        player.setHand(buildHand(1, 2, 3, 4, 10, 20, 30, 31, 32));
        assertEquals(player.chooseAtout(true), Card.COLOR_SPADE);
    }

    @Test
    public void test_played_card() throws PlayerLeftExpection, IllegalAccessException {
        var thirdPlayer = new ArtificialPlayer(3, "GC");
        var fourthPlayer = new ArtificialPlayer(0, "Mono");
        Card.atout = Card.COLOR_DIAMOND;
        player.setPlayerInfo(otherPlayer);
        player.setPlayerInfo(thirdPlayer);
        player.setPlayerInfo(fourthPlayer);
        player.setHand(buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33));
        assertEquals(unknownCardsInGame.size(), 27);

        player.setPlayedCard(otherPlayer, new Card(Card.RANK_8, Card.COLOR_SPADE));
        currentPlie = (Plie) currentPlieField.get(player);
        assertEquals(currentPlie.getSize(), 1);
        assertEquals(unknownCardsInGame.size(), 26);

        // player does not follow
        player.setPlayedCard(thirdPlayer, new Card(Card.RANK_8, Card.COLOR_HEART));
        assertEquals(currentPlie.getSize(), 2);
        assertEquals(unknownCardsInGame.size(), 25);
        for (int i=0; i<9; i++) {
            if (i == 2 || i == 1 || i == 4) {
                continue;
            }
            assertEquals(unknownCardsInGame.get(i)[1], 0);
            assertEquals(unknownCardsInGame.get(i)[0], 0.5f);
            assertEquals(unknownCardsInGame.get(i)[2], 0.5f);
        }
        assertEquals(25, unknownCardsInGame.size());

        // test when player does not follow trump card
        player.collectPlie(fourthPlayer);
        player.setPlayedCard(otherPlayer, new Card(Card.RANK_10, Card.atout));
        assertEquals(24, unknownCardsInGame.size());
        player.setPlayedCard(thirdPlayer, new Card(Card.RANK_NELL, Card.COLOR_CLUB));
        assertEquals(23, unknownCardsInGame.size());
        var exceptions = new ArrayList<>(Arrays.asList(30, 31, 32, 33));
        for (int i=27; i<36; i++) {
            if (exceptions.contains(i)) {
                continue;
            }
            assertEquals(unknownCardsInGame.get(i)[1], 0);
        }
        // bourg probability is not affected
        assertEquals(unknownCardsInGame.get(32)[1], 1/3f);
    }

    @Test
    public void test_play() throws PlayerLeftExpection {
        var secondPlayer = new ArtificialPlayer(0, "Wein");
        var fourthPlayer = new ArtificialPlayer(3, "Hhip");
        player.setPlayerInfo(secondPlayer);
        player.setPlayerInfo(otherPlayer);
        player.setPlayerInfo(fourthPlayer);
        player.setPlayersOrder(Arrays.asList(1, 3, 0, 2));

        var originalHand = buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33);
        player.setHand(originalHand);
        player.setAtout(Card.COLOR_HEART, otherPlayer);
        var card = player.play();
        assertEquals(8, hand.size());
        assertFalse(hand.contains(card));
        assertTrue(originalHand.contains(card));
    }

    @Test
    public void test_get_announcements() throws PlayerLeftExpection {
        var secondPlayer = new ArtificialPlayer(0, "Wein");
        var fourthPlayer = new ArtificialPlayer(3, "Hhip");
        player.setPlayerInfo(secondPlayer);
        player.setPlayerInfo(otherPlayer);
        player.setPlayerInfo(fourthPlayer);
        player.setPlayersOrder(Arrays.asList(1, 3, 0, 2));
        player.setHand(buildHand(1, 2, 3, 9, 10, 11, 12, 21, 30));
        player.setAtout(Card.COLOR_HEART, otherPlayer);
        player.play();
        assertEquals(8, hand.size());
        var an = player.getAnnouncements();
        assertEquals(3, an.size());
        assertTrue(an.contains(new Announcement(Announcement.THREE_CARDS, new Card(3))));
        assertTrue(an.contains(new Announcement(Announcement.FIFTY, new Card(12))));
        assertTrue(an.contains(new Announcement(Announcement.NELL_SQUARE, new Card(Card.RANK_NELL, Card.COLOR_SPADE))));
        player.play();
        an = player.getAnnouncements();
        assertEquals(0, an.size());
    }

    @Test
    public void test_set_announcements() throws PlayerLeftExpection {
        player.setPlayerInfo(otherPlayer);
        player.setHand(buildHand(26, 27, 28, 29, 30, 32, 33, 34, 35));
        player.setAnnouncements(otherPlayer, Collections.singletonList(new Announcement(Announcement.THREE_CARDS, new Card(24))));

        assertEquals(24, unknownCardsInGame.size());
        assertEquals(3, knownCardsInHands[0].size());
        assertTrue(knownCardsInHands[0].contains(new Card(24)));
        assertTrue(knownCardsInHands[0].contains(new Card(23)));
        assertTrue(knownCardsInHands[0].contains(new Card(22)));
        assertEquals(0, unknownCardsInGame.get(25)[0]);
        assertEquals(0.5f, unknownCardsInGame.get(25)[1]);
        assertEquals(0, unknownCardsInGame.get(21)[0]);

        player.setAnnouncements(otherPlayer, Collections.singletonList(new Announcement(Announcement.STOECK, null)));

        player.setAnnouncements(otherPlayer, Collections.singletonList(new Announcement(Announcement.FIFTY, new Card(3))));
        player.setAnnouncements(otherPlayer, Collections.singletonList(new Announcement(Announcement.FIFTY, new Card(17))));
    }

    @Test
    public void test_stoeck() throws PlayerLeftExpection, IllegalAccessException {
        Card.atout = Card.COLOR_DIAMOND;
        player.setHand(buildHand(0, 2, 4, 29, 30, 8, 33, 34, 6));
        player.setAtout(Card.atout, otherPlayer);
        hand.remove(new Card(6));
        var an = player.getAnnouncements();
        assertTrue(an.isEmpty());

        playedCardField.set(player, new Card(2));
        hand.remove(new Card(8));
        an = player.getAnnouncements();
        assertTrue(an.isEmpty());

        hand.remove(new Card(33));
        playedCardField.set(player, new Card(33));
        an = player.getAnnouncements();
        assertTrue(an.isEmpty());

        hand.remove(new Card(34));
        playedCardField.set(player, new Card(34));
        an = player.getAnnouncements();
        assertEquals(1, an.size());
    }
}
