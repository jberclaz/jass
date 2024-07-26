package com.leflat.jass.client;

import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.Team;
import com.leflat.jass.server.RulesTests;
import com.leflat.jass.test.MockUi;
import org.junit.jupiter.api.Test;

import  com.leflat.jass.common.Card;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class InteractivePlayerTests {
    @Test
    void interactive_player_test() {
        var ui = new MockUi(0, 1);
        var player = new InteractivePlayer(ui, 0, "GC", 0);
        player.setPlayerInfo(new ClientPlayer(1, "Berte"));
        player.setPlayerInfo(new ClientPlayer(2, "Pischus"));
        player.chooseTeamSelectionMethod();
        player.prepareTeamDrawing(true);
        player.prepareTeamDrawing(false);
        var card = player.drawCard();
        assertTrue(0 <= card && card < Card.DECK_SIZE);
        player.setCard(new ClientPlayer(2, "Pischus"), 10, new Card(20));
        player.setCard(new ClientPlayer(3, "Wein"), 10, new Card(20));

        player.setPlayersOrder(Arrays.asList(3, 2, 1, 0));
        var partner = player.choosePartner();
        assertTrue(0 <= partner && partner < 4);
        player.setHand(RulesTests.buildHand(0, 1, 2, 3, 4, 5, 6, 7, 8));

        var atout = player.chooseAtout(true);
        atout = player.chooseAtout(false);
        player.setAtout(2, new ClientPlayer(0, "GC"));

        var cardPlayed = player.play();
        player.setPlayedCard(new ClientPlayer(1, "Berte"), new Card(10));
        player.collectPlie(new ClientPlayer(2, "Pischus"));
        player.setScores(10, 20);
        var announcements = player.getAnnouncements();
        assertEquals(2, announcements.size());

        player.setAnnouncements(new ClientPlayer(1, "Berte"), Arrays.asList(new Announcement(Announcement.FIFTY, new Card(10)), new Announcement(Announcement.STOECK, new Card(14))));
        var team = new Team(0);
        team.addPlayer(new ClientPlayer(0, "GC"));
        team.addPlayer(new ClientPlayer(1, "Berte"));
        player.setGameResult(team);

        player.getNewGame();
        player.playerLeft(new ClientPlayer(1, "Pischus"));
        player.lostServerConnection();

        player.setHandScore(10, 20, null);
        player.setHandScore(20, 30, team);
    }
}
