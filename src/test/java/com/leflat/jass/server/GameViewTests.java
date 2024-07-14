package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.leflat.jass.server.RulesTests.buildHand;
import static org.junit.jupiter.api.Assertions.*;

public class GameViewTests {
    GameView gameView;
    Field unknownCardsInGameField, knownCardsInHandsField, handSizesField;
    int[] handSizes;
    Map<Integer, Float[]> unknownCardsInGame;
    List<Card>[] knownCardsInHands;

    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        gameView = new GameView();
        unknownCardsInGameField = GameView.class.getDeclaredField("unknownCardsInGame");
        knownCardsInHandsField = GameView.class.getDeclaredField("knownCardsInHands");
        handSizesField = GameView.class.getDeclaredField("handSizes");
        unknownCardsInGameField.setAccessible(true);
        knownCardsInHandsField.setAccessible(true);
        handSizesField.setAccessible(true);
        handSizes = (int[]) handSizesField.get(gameView);
        unknownCardsInGame = (Map<Integer, Float[]>) unknownCardsInGameField.get(gameView);
        knownCardsInHands = (List<Card>[]) knownCardsInHandsField.get(gameView);
        gameView.reset(buildHand(1, 3, 5, 7, 9, 11, 13, 15, 17));
    }

    @Test
    public void test_reset() {
        for (int p = 0; p < 3; p++) {
            assertEquals(handSizes[p], 9);
        }
        assertEquals(unknownCardsInGame.size(), 27);
    }

    @Test
    public void test_card_played() {
        gameView.cardPlayed(0, new Card(0));
        assertEquals(unknownCardsInGame.size(), 26);

        gameView.playerHasCard(2, 10);
        assertEquals(unknownCardsInGame.size(), 25);
        assertEquals(knownCardsInHands[2].size(), 1);

        gameView.cardPlayed(2, new Card(10));
        assertEquals(unknownCardsInGame.size(), 25);
        assertEquals(knownCardsInHands[2].size(), 0);

        // play same card twice
        assertThrows(RuntimeException.class, () -> gameView.cardPlayed(2, new Card(10)));

        //
        knownCardsInHands[0].add(new Card(2));
        assertThrows(RuntimeException.class, () -> gameView.cardPlayed(0, new Card(2)));
    }

    @Test
    public void test_player_has_card() {
        gameView.playerHasCard(2, 10);
        assertEquals(unknownCardsInGame.size(), 26);
        assertEquals(knownCardsInHands[2].size(), 1);
        assertEquals(knownCardsInHands[2].get(0), new Card(10));

        gameView.playerHasCard(2, new Card(12));
        assertEquals(unknownCardsInGame.size(), 25);
        assertEquals(knownCardsInHands[2].size(), 2);

        assertDoesNotThrow(() -> {
            gameView.playerHasCard(2, new Card(12));
        });
        assertDoesNotThrow(() -> gameView.playerHasCard(1, new Card(12)));
    }

    @Test
    public void test_player_doesnot_have_card() {
        gameView.playerDoesNotHaveCard(0, 14);
        assertEquals(unknownCardsInGame.size(), 27);
        assertEquals(unknownCardsInGame.get(14)[0], 0f);
        assertEquals(unknownCardsInGame.get(14)[1], 0.5f);
        assertEquals(unknownCardsInGame.get(14)[2], 0.5f);

        gameView.playerDoesNotHaveCard(2, 14);
        assertEquals(unknownCardsInGame.size(), 26);
        assertEquals(knownCardsInHands[1].size(), 1);
        assertEquals(knownCardsInHands[1].get(0), new Card(14));

        assertThrows(RuntimeException.class, () -> gameView.playerDoesNotHaveCard(1, 14));

        gameView.playerDoesNotHaveCard(2, 1);
        assertEquals(unknownCardsInGame.size(), 26);
    }

    @Test
    public void test_to_string() {
        for (int i = 0; i < 20; i += 2) {
            gameView.cardPlayed((i / 2) % 3, new Card(i));
        }
        for (int i = 19; i < 30; i++) {
            gameView.cardPlayed(i % 3, new Card(i));
        }
        gameView.playerHasCard(0, 30);
        gameView.playerHasCard(1, 31);
        gameView.playerHasCard(2, 32);
        gameView.playerHasCard(2, 33);
        gameView.playerDoesNotHaveCard(0, 34);
        assertEquals(gameView.toString(), String.join("\n", "roi de carreau : 0.0, 0.5, 0.5",
                "as de carreau : 0.33333334, 0.33333334, 0.33333334",
                "0: nell de carreau ",
                "1: dix de carreau ",
                "2: bourg de carreau dame de carreau \n"));
    }

    @Test
    public void test_get_number_cads_in_game() {
        assertEquals(gameView.getNumberCardsInGame(), 27);
        for (int i = 20; i < 25; i++) {
            gameView.playerHasCard(i % 3, i);
            assertEquals(gameView.getNumberCardsInGame(), 27);
        }
        for (int i = 20; i < 30; i++) {
            gameView.cardPlayed(i % 3, new Card(i));
            assertEquals(gameView.getNumberCardsInGame(), 27 - i + 19);
        }
        for (int i = 30; i < Card.DECK_SIZE; i++) {
            gameView.playerHasCard(i % 3, new Card(i));
            assertEquals(gameView.getNumberCardsInGame(), 17);
        }
    }

    @Test
    public void test_get_random_hands() {
        var hands = gameView.getRandomHands();
        assertEquals(hands.length, 3);
        for (int h = 0; h < 3; h++) {
            assertEquals(hands[h].size(), 9);
        }

        gameView.cardPlayed(0, new Card(30));
        hands = gameView.getRandomHands();
        assertEquals(hands[0].size(), 8);

        gameView.playerHasCard(0, 31);
        gameView.playerHasCard(0, 32);
        gameView.playerHasCard(1, 33);
        hands = gameView.getRandomHands();
        assertTrue(hands[0].contains(new Card(31)));
        assertTrue(hands[0].contains(new Card(32)));
        assertEquals(hands[0].size(), 8);
        assertTrue(hands[1].contains(new Card(33)));
        assertEquals(hands[1].size(), 9);

        gameView.playerDoesNotHaveCard(2, 20);
        gameView.playerDoesNotHaveCard(2, 21);
        gameView.playerDoesNotHaveCard(2, 22);
        hands = gameView.getRandomHands();
        assertFalse(hands[2].contains(new Card(20)));
        assertFalse(hands[2].contains(new Card(21)));
        assertFalse(hands[2].contains(new Card(22)));
    }
}
