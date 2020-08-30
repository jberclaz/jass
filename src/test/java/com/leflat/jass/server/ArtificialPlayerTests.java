package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import static com.leflat.jass.server.RulesTests.buildHand;
import static org.junit.jupiter.api.Assertions.*;

public class ArtificialPlayerTests {
    ArtificialPlayer player;
    Field playersPositionField, gameViewField;
    ArtificialPlayer otherPlayer;

    @BeforeEach
    public void setUp() throws NoSuchFieldException {
        player = new ArtificialPlayer(1, "name");
        playersPositionField = ArtificialPlayer.class.getDeclaredField("playersPositions");
        playersPositionField.setAccessible(true);
        gameViewField = ArtificialPlayer.class.getDeclaredField("gameView");
        gameViewField.setAccessible(true);
        otherPlayer = new ArtificialPlayer(2, "Pischus");
    }

    @Test
    public void test_set_player_info() throws IllegalAccessException {
        player.setPlayerInfo(otherPlayer);
        var playersPosition = (Map<Integer, Integer>)playersPositionField.get(player);
        assertEquals(playersPosition.size(), 1);
        assertTrue(playersPosition.containsKey(2));
        assertEquals(playersPosition.get(2), 1);
    }

    @Test
    public void test_draw_card() {
        for (int i=0; i<35; i++) {
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
        var playersPosition = (Map<Integer, Integer>)playersPositionField.get(player);
        assertEquals(playersPosition.size(), 3);
        assertEquals(playersPosition.get(0), 3);

        player.setPlayersOrder(Arrays.asList(3, 2, 0, 1));
        assertEquals(playersPosition.get(3), 1);
        assertEquals(playersPosition.get(2), 2);
        assertEquals(playersPosition.get(0), 3);
    }

    @Test
    public void test_set_hand() throws PlayerLeftExpection {
        player.setHand(buildHand(1, 4, 9, 10, 12, 20, 22, 30, 33));
    }

}
