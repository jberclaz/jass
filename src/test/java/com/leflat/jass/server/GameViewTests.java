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
    Field cardsInGameField, cardsInHandsField, handSizesField;
    Map<Integer, Integer> handSizes;
    Map<Integer, Float[]> cardsInGame;
    Map<Integer, List<Card>> cardsInHands;

    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        gameView = new GameView();
        cardsInGameField = GameView.class.getDeclaredField("cardsInGame");
        cardsInHandsField = GameView.class.getDeclaredField("cardsInHands");
        handSizesField = GameView.class.getDeclaredField("handSizes");
        cardsInGameField.setAccessible(true);
        cardsInHandsField.setAccessible(true);
        handSizesField.setAccessible(true);
        handSizes = (Map<Integer, Integer>) handSizesField.get(gameView);
        cardsInGame = (Map<Integer, Float[]>) cardsInGameField.get(gameView);
        cardsInHands = (Map<Integer, List<Card>>) cardsInHandsField.get(gameView);
        gameView.reset(buildHand(1, 3, 5, 7, 9, 11, 13, 15, 17));
    }

    @Test
    public void test_reset() throws IllegalAccessException {
        for (int p = 0; p < 3; p++) {
            assertEquals(handSizes.get(p), 9);
        }
        assertEquals(cardsInGame.size(), 27);
    }

    @Test
    public void test_card_played() {
        gameView.cardPlayed(0, new Card(0));
        assertEquals(cardsInGame.size(), 26);

        gameView.playerHasCard(2, 10);
        assertEquals(cardsInGame.size(), 25);
        assertEquals(cardsInHands.get(2).size(), 1);

        gameView.cardPlayed(2, new Card(10));
        assertEquals(cardsInGame.size(), 25);
        assertEquals(cardsInHands.get(2).size(), 0);
    }

    @Test
    public void test_player_has_card() {
        gameView.playerHasCard(2, 10);
        assertEquals(cardsInGame.size(), 26);
        assertEquals(cardsInHands.get(2).size(), 1);

        gameView.playerHasCard(2, new Card(12));
        assertEquals(cardsInGame.size(), 25);
        assertEquals(cardsInHands.get(2).size(), 2);
    }

    @Test
    public void test_player_doesnot_have_card() {
        gameView.playerDoesNotHaveCard(0, 14);
        assertEquals(cardsInGame.size(), 27);
        assertEquals(cardsInGame.get(14)[0], 0f);

        gameView.playerDoesNotHaveCard(2, 14);
        assertEquals(cardsInGame.size(), 26);
        assertEquals(cardsInHands.get(1).size(), 1);

        gameView.playerDoesNotHaveCard(2, 1);
        assertEquals(cardsInGame.size(), 26);
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
        for (int i = 30; i < 36; i++) {
            gameView.playerHasCard(i % 3, new Card(i));
            assertEquals(gameView.getNumberCardsInGame(), 17);
        }
    }

    @Test
    public void test_get_random_hands() {
        var hands = gameView.getRandomHands();
        assertEquals(hands.size(), 3);
        for (int h = 0; h < 3; h++) {
            assertEquals(hands.get(h).size(), 9);
        }

        gameView.cardPlayed(0, new Card(30));
        hands = gameView.getRandomHands();
        assertEquals(hands.get(0).size(), 8);

        gameView.playerHasCard(0, 31);
        gameView.playerHasCard(0, 32);
        gameView.playerHasCard(1, 33);
        hands = gameView.getRandomHands();
        assertTrue(hands.get(0).contains(new Card(31)));
        assertTrue(hands.get(0).contains(new Card(32)));
        assertEquals(hands.get(0).size(), 8);
        assertTrue(hands.get(1).contains(new Card(33)));
        assertEquals(hands.get(1).size(), 9);

        gameView.playerDoesNotHaveCard(2, 20);
        gameView.playerDoesNotHaveCard(2, 21);
        gameView.playerDoesNotHaveCard(2, 22);
        hands = gameView.getRandomHands();
        assertFalse(hands.get(2).contains(new Card(20)));
        assertFalse(hands.get(2).contains(new Card(21)));
        assertFalse(hands.get(2).contains(new Card(22)));
    }
}
