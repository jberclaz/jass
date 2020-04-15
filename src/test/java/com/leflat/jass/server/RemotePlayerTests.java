package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.RemoteCommand;
import com.leflat.jass.server.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemotePlayerTests {
    private MockServerNetwork network;
    private RemotePlayer player;

    @BeforeEach
    public void setUp() {
        network = new MockServerNetwork();
        network.addAnswer(new String[]{"GC"});
        try {
            player = new RemotePlayer(0, network);
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
        network.clearParameters();
    }

    @Test
    public void test_player_info() throws PlayerLeftExpection {
        var testPlayer = new TestPlayer(2);
        testPlayer.setName("GC");
        player.setPlayerInfo(testPlayer);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_PLAYER_INFO, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(2, Integer.parseInt(network.sendParameters.get(0).get(1)));
        assertEquals("GC", network.sendParameters.get(0).get(2));
    }

    @Test
    public void choose_team_method_test() throws PlayerLeftExpection {
        network.addAnswer(new String[]{"RANDOM"});
        player.chooseTeamSelectionMethod();
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.CHOOSE_TEAM_SELECTION_METHOD, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void prepare_team_drawing_test() throws PlayerLeftExpection {
        player.prepareTeamDrawing(true);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.PREPARE_TEAM_DRAWING, Integer.parseInt(network.sendParameters.get(0).get(0)));
        network.clearParameters();
        player.prepareTeamDrawing(false);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.RESTART_TEAM_DRAWING, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void draw_card_test() throws PlayerLeftExpection {
        network.addAnswer(new String[]{"32"});
        int answer = player.drawCard();
        assertEquals(32, answer);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.DRAW_CARD, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void set_card_test() throws PlayerLeftExpection {
        var testPlayer = new TestPlayer(1);
        player.setCard(testPlayer, 5, new Card(10));
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_CARD, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(1, Integer.parseInt(network.sendParameters.get(0).get(1)));
        assertEquals(5, Integer.parseInt(network.sendParameters.get(0).get(2)));
        assertEquals(10, Integer.parseInt(network.sendParameters.get(0).get(3)));
    }

    @Test
    public void set_player_order_test() throws PlayerLeftExpection {
        player.setPlayersOrder(Arrays.asList(3, 1, 2, 0));
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_PLAYERS_ORDER, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(3, Integer.parseInt(network.sendParameters.get(0).get(1)));
        assertEquals(1, Integer.parseInt(network.sendParameters.get(0).get(2)));
        assertEquals(2, Integer.parseInt(network.sendParameters.get(0).get(3)));
        assertEquals(0, Integer.parseInt(network.sendParameters.get(0).get(4)));
    }

    @Test
    public void choose_partner_test() throws PlayerLeftExpection {
        network.addAnswer(new String[]{"3"});
        int partner = player.choosePartner();
        assertEquals(3, partner);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.CHOOSE_PARTNER, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void set_hand_test() throws PlayerLeftExpection {
        var cards = RulesTests.buildHand(10, 12, 14, 16, 18, 20, 22, 24, 26);
        player.setHand(cards);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_HAND, Integer.parseInt(network.sendParameters.get(0).get(0)));
        for (int i = 0; i < 9; i++) {
            assertEquals(10 + i * 2, Integer.parseInt(network.sendParameters.get(0).get(i + 1)));
        }
    }

    @Test
    public void choose_atout_test() throws PlayerLeftExpection {
        network.addAnswer(new String[]{"2"});
        int atout = player.chooseAtout(true);
        assertEquals(2, atout);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.CHOOSE_ATOUT, Integer.parseInt(network.sendParameters.get(0).get(0)));

        network.clearParameters();
        network.addAnswer(new String[]{"1"});
        atout = player.chooseAtout(false);
        assertEquals(1, atout);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.CHOOSE_ATOUT_SECOND, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void set_atout_test() throws PlayerLeftExpection {
        player.setAtout(Card.COLOR_HEART, new TestPlayer(2));
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_ATOUT, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(Card.COLOR_HEART, Integer.parseInt(network.sendParameters.get(0).get(1)));
        assertEquals(2, Integer.parseInt(network.sendParameters.get(0).get(2)));
    }

    @Test
    public void play_test() throws PlayerLeftExpection {
        network.addAnswer(new String[]{"12"});
        Card card = player.play();
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.PLAY, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(12, card.getNumber());
    }

    @Test
    public void set_played_card_test() throws PlayerLeftExpection {
        player.setPlayedCard(new TestPlayer(3), new Card(13));
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.SET_PLAYED_CARD, Integer.parseInt(network.sendParameters.get(0).get(0)));
        assertEquals(3, Integer.parseInt(network.sendParameters.get(0).get(1)));
        assertEquals(13, Integer.parseInt(network.sendParameters.get(0).get(2)));
    }
}
