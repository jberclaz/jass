package com.leflat.jass.server;

import com.leflat.jass.client.*;
import com.leflat.jass.common.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ServerTests {
    @Test
    void player_position_test() {
        Method getPlayerPosition = null;
        try {
            getPlayerPosition = GameController.class.getDeclaredMethod("getPlayerPosition", BasePlayer.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        getPlayerPosition.setAccessible(true);

        RemotePlayer p1, p2, p3, p4;
        var game = new GameController(0);
        try {
            p1 = new RemotePlayer(1, new MockServerNetwork(new String[]{"GC"}));
            p2 = new RemotePlayer(2, new MockServerNetwork(new String[]{"Pischus"}));
            p3 = new RemotePlayer(3, new MockServerNetwork(new String[]{"Berte"}));
            p4 = new RemotePlayer(4, new MockServerNetwork(new String[]{"Wein"}));
            game.addPlayer(p2);
            game.addPlayer(p1);
            game.addPlayer(p4);
            game.addPlayer(p3);
        } catch (PlayerLeftExpection ignored) {
            return;
        }

        try {
            int position = (Integer) getPlayerPosition.invoke(game, p4);
            assertEquals(2, position);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Test
    void add_players_test() {
        RemotePlayer p1, p2, p3, p4;
        var game = new GameController(120);
        assertEquals(0, game.getNbrPlayers());
        assertEquals(120, game.getGameId());
        try {
            p1 = new RemotePlayer(1, new MockServerNetwork(new String[]{"GC"}));
            p2 = new RemotePlayer(2, new MockServerNetwork(new String[]{"Pischus"}));
            p3 = new RemotePlayer(3, new MockServerNetwork(new String[]{"Berte"}));
            p4 = new RemotePlayer(4, new MockServerNetwork(new String[]{"Wein"}));
            game.addPlayer(p2);
            assertEquals(1, game.getNbrPlayers());
            assertFalse(game.isGameFull());
            game.addPlayer(p1);
            game.addPlayer(p4);
            game.addPlayer(p3);
            assertTrue(game.isGameFull());
        } catch (PlayerLeftExpection ignored) {
            return;
        }
    }

    @Test
    void test_draw_cards() {
        Method drawCards = null;
        try {
            drawCards = GameController.class.getDeclaredMethod("drawCards");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        drawCards.setAccessible(true);

        List<RemotePlayer> players = new ArrayList<>();
        List<MockServerNetwork> networks = new ArrayList<>();
        var game = new GameController(0);
        try {
            for (int i = 0; i < 4; i++) {
                networks.add(new MockServerNetwork(new String[]{"Wein"}));
                players.add(new RemotePlayer(i, networks.get(i)));
                game.addPlayer(players.get(i));
            }
        } catch (PlayerLeftExpection ignored) {
            return;
        }

        try {
            int startingPlayer = (int) drawCards.invoke(game);
            for (int i = 0; i < 4; i++) {
                assertEquals(5, networks.get(i).sendParameters.size());
            }
            var parameters = networks.get(startingPlayer).sendParameters.get(4);
            assertEquals(parameters.size(), 10);
            assertEquals(RemoteCommand.SET_HAND, Integer.parseInt(parameters.get(0)));
            assertTrue(parameters.stream().anyMatch(p -> Integer.parseInt(p) == Card.DIAMOND_SEVEN));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Test
    void reorder_player_test() {
        Method reorderPlayers = null;
        try {
            reorderPlayers = GameController.class.getDeclaredMethod("reorderPlayers");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        reorderPlayers.setAccessible(true);

        Field teamsField = null;
        try {
            teamsField = GameController.class.getDeclaredField("teams");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        teamsField.setAccessible(true);

        Field playersField = null;
        try {
            playersField = GameController.class.getDeclaredField("players");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        playersField.setAccessible(true);

        var game = new GameController(0);
        List<RemotePlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            try {
                var player = new RemotePlayer(i, new MockServerNetwork(new String[]{"Hhip"}));
                players.add(player);
                game.addPlayer(player);
            } catch (PlayerLeftExpection playerLeftExpection) {
                playerLeftExpection.printStackTrace();
            }
        }

        var teams = new Team[2];
        teams[0] = new Team(0);
        teams[1] = new Team(1);
        teams[0].addPlayer(players.get(3));
        teams[0].addPlayer(players.get(2));
        teams[1].addPlayer(players.get(0));
        teams[1].addPlayer(players.get(1));
        try {
            teamsField.set(game, teams);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        try {
            reorderPlayers.invoke(game);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        try {
            var privatePlayers = (ArrayList<RemotePlayer>) playersField.get(game);
            assertEquals(3, privatePlayers.get(0).getId());
            assertEquals(0, privatePlayers.get(1).getId());
            assertEquals(2, privatePlayers.get(2).getId());
            assertEquals(1, privatePlayers.get(3).getId());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Test
    void test_full_game() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        game.addPlayer(new MockRemotePlayer(0, "Mono"));
        game.addPlayer(new MockRemotePlayer(1, "Berte"));
        game.addPlayer(new MockRemotePlayer(2, "GC"));
        game.addPlayer(new MockRemotePlayer(3, "Pischus"));

        game.run();
    }

    @Test
    void test_client_server_transmission() throws IOException, PlayerLeftExpection, InterruptedException, ServerDisconnectedException {
        IPlayer mockedPlayer = mock(JassPlayer.class);
        var hand = RulesTests.buildHand(1, 2, 3, 4, 5, 6, 7, 8, 9);
        var otherPlayer = new ClientPlayer(2, "GC");
        var card = new Card(31);
        var order = new Integer[]{3, 1, 0, 2};
        var anouncement = Collections.singletonList(new Announcement(Announcement.HUNDRED, new Card(Card.RANK_ROI, Card.COLOR_CLUB)));
        var stoeck = Collections.singletonList(new Announcement(Announcement.STOECK, null));
        var team = new Team(0);
        team.addPlayer(otherPlayer);
        team.addPlayer(new ClientPlayer(3, "Layenne"));

        when(mockedPlayer.choosePartner()).thenReturn(3);
        when(mockedPlayer.chooseTeamSelectionMethod()).thenReturn(TeamSelectionMethod.RANDOM);
        when(mockedPlayer.drawCard()).thenReturn(23);
        when(mockedPlayer.chooseAtout(true)).thenReturn(4);
        when(mockedPlayer.play()).thenReturn(card);
        when(mockedPlayer.getAnnouncements()).thenReturn(anouncement);
        when(mockedPlayer.getNewGame()).thenReturn(true);

        var serverOutput = new PipedOutputStream();
        var clientOutput = new PipedOutputStream();
        var clientInput = new PipedInputStream(serverOutput);
        var serverInput = new PipedInputStream(clientOutput);
        var serverThread = new Thread(() -> {
            var serverNetwork = new LocalServerNetwork(serverInput, serverOutput, 1);
            try {
                var remotePlayer = new RemotePlayer(1, serverNetwork);
                remotePlayer.setPlayerInfo(otherPlayer);
                remotePlayer.setScores(10, 20);
                assertEquals(TeamSelectionMethod.RANDOM, remotePlayer.chooseTeamSelectionMethod());
                remotePlayer.prepareTeamDrawing(true);
                assertEquals(23, remotePlayer.drawCard());
                remotePlayer.setCard(otherPlayer, 12, card);
                remotePlayer.setPlayersOrder(Arrays.asList(order));
                assertEquals(3, remotePlayer.choosePartner());
                remotePlayer.setHand(hand);
                assertEquals(4, remotePlayer.chooseAtout(true));
                remotePlayer.setAtout(Card.COLOR_HEART, otherPlayer);
                assertEquals(card, remotePlayer.play());
                remotePlayer.setPlayedCard(otherPlayer, card);
                remotePlayer.collectPlie(otherPlayer);
                var result = remotePlayer.getAnnouncements();
                assertEquals(anouncement.size(), result.size());
                assertEquals(anouncement.get(0), result.get(0));
                remotePlayer.setAnnouncements(otherPlayer, anouncement);
                remotePlayer.setAnnouncements(otherPlayer, stoeck);
                remotePlayer.setGameResult(team);
                assertTrue(remotePlayer.getNewGame());
                remotePlayer.playerLeft(otherPlayer);
                remotePlayer.setHandScore(100, 57, null);
                remotePlayer.setHandScore(0, 257, team);
            } catch (PlayerLeftExpection playerLeftExpection) {
                playerLeftExpection.printStackTrace();
            }
        });

        serverThread.start();

        var clientNetwork = new LocalClientNetwork(clientInput, clientOutput);
        var remoteController = new RemoteController(mockedPlayer, clientNetwork);
        var remoteThread = new Thread(remoteController);
        remoteThread.start();

        serverThread.join();

        verify(mockedPlayer, times(1)).setPlayerInfo(otherPlayer);
        verify(mockedPlayer, times(1)).setScores(10, 20);
        verify(mockedPlayer, times(1)).chooseTeamSelectionMethod();
        verify(mockedPlayer, times(1)).prepareTeamDrawing(true);
        verify(mockedPlayer, times(1)).drawCard();
        verify(mockedPlayer, times(1)).setCard(otherPlayer, 12, card);
        verify(mockedPlayer, times(1)).setPlayersOrder(Arrays.asList(order));
        verify(mockedPlayer, times(1)).choosePartner();
        verify(mockedPlayer, times(1)).setHand(hand);
        verify(mockedPlayer, times(1)).chooseAtout(true);
        verify(mockedPlayer, times(1)).setAtout(Card.COLOR_HEART, otherPlayer);
        verify(mockedPlayer, times(1)).play();
        verify(mockedPlayer, times(1)).setPlayedCard(otherPlayer, card);
        verify(mockedPlayer, times(1)).collectPlie(otherPlayer);
        verify(mockedPlayer, times(1)).getAnnouncements();
        verify(mockedPlayer, times(1)).setAnnouncements(otherPlayer, anouncement);
        verify(mockedPlayer, times(1)).setAnnouncements(otherPlayer, stoeck);
        verify(mockedPlayer, times(1)).getNewGame();
        verify(mockedPlayer, times(1)).playerLeft(otherPlayer);
        verify(mockedPlayer, times(1)).setHandScore(100, 57, null);
        verify(mockedPlayer, times(1)).setHandScore(0, 257, team);
        verify(mockedPlayer, times(1)).setGameResult(team);
    }

    @Test
    void test_pick_teammeates() throws PlayerLeftExpection {
        RemotePlayer player = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        when(player.choosePartner()).thenReturn(1);
        when(player.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        var game = new GameController(0);
        game.addPlayer(player);
        game.addPlayer(player2);
        game.pickTeamMates();
        verify(player, times(1)).choosePartner();
        assertEquals(0, game.getTeams()[0].getPlayer(0).getId());
        assertEquals(1, game.getTeams()[0].getPlayer(1).getId());
    }

    @Test
    void test_calculate_team() throws PlayerLeftExpection {
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        var game = new GameController(0);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);
        Map<BasePlayer, Card> choosenCards = new HashMap<>();
        choosenCards.put(player1, new Card(Card.RANK_DAME, Card.COLOR_HEART));
        choosenCards.put(player2, new Card(Card.RANK_10, Card.COLOR_HEART));
        choosenCards.put(player3, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND));
        choosenCards.put(player4, new Card(Card.RANK_7, Card.COLOR_CLUB));
        assertTrue(game.calculateTeam(choosenCards));
        assertEquals(0, game.getTeams()[0].getPlayer(0).getId());
        assertEquals(3, game.getTeams()[0].getPlayer(1).getId());
        assertEquals(1, game.getTeams()[1].getPlayer(0).getId());
        assertEquals(2, game.getTeams()[1].getPlayer(1).getId());

        game.getTeams()[0].reset();
        game.getTeams()[1].reset();
        choosenCards.put(player1, new Card(Card.RANK_10, Card.COLOR_HEART));
        choosenCards.put(player2, new Card(Card.RANK_7, Card.COLOR_HEART));
        choosenCards.put(player3, new Card(Card.RANK_10, Card.COLOR_DIAMOND));
        choosenCards.put(player4, new Card(Card.RANK_DAME, Card.COLOR_CLUB));
        assertTrue(game.calculateTeam(choosenCards));
        assertEquals(1, game.getTeams()[0].getPlayer(0).getId());
        assertEquals(3, game.getTeams()[0].getPlayer(1).getId());
        assertEquals(0, game.getTeams()[1].getPlayer(0).getId());
        assertEquals(2, game.getTeams()[1].getPlayer(1).getId());

        game.getTeams()[0].reset();
        game.getTeams()[1].reset();
        choosenCards.put(player1, new Card(Card.RANK_DAME, Card.COLOR_HEART));
        choosenCards.put(player2, new Card(Card.RANK_7, Card.COLOR_HEART));
        choosenCards.put(player3, new Card(Card.RANK_10, Card.COLOR_DIAMOND));
        choosenCards.put(player4, new Card(Card.RANK_DAME, Card.COLOR_CLUB));
        assertFalse(game.calculateTeam(choosenCards));
    }

    @Test
    void test_choose_atout() throws PlayerLeftExpection {
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        when(player2.chooseAtout(false)).thenReturn(2);
        when(player4.chooseAtout(true)).thenReturn(4);
        var game = new GameController(0);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);
        int atout = game.chooseAtout(3);
        assertEquals(2, atout);
        verify(player4, times(1)).chooseAtout(true);
        verify(player2, times(1)).chooseAtout(false);
    }

    @Test
    void test_process_announcements() throws PlayerLeftExpection {
        Card.atout = Card.COLOR_SPADE;
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        when(player1.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player2.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player3.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player4.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player1.choosePartner()).thenReturn(1);

        var game = new GameController(0);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        game.pickTeamMates();
        assertFalse(game.processAnnouncements());
        assertEquals(0, game.getTeams()[0].getScore());
        assertEquals(0, game.getTeams()[1].getScore());
        verify(player1, times(1)).getAnnouncements();
        verify(player2, times(1)).getAnnouncements();
        verify(player3, times(1)).getAnnouncements();
        verify(player4, times(1)).getAnnouncements();

        List<Announcement> an1 = new ArrayList<>();
        an1.add(new Announcement(Announcement.STOECK, null));
        an1.add(new Announcement(Announcement.THREE_CARDS, new Card(Card.RANK_ROI, Card.COLOR_SPADE)));
        when(player1.getAnnouncements()).thenReturn(an1);
        when(player2.getAnnouncements()).thenReturn(Collections.singletonList(new Announcement(Announcement.FIFTY, new Card(23))));
        when(player4.getAnnouncements()).thenReturn(Collections.singletonList(new Announcement(Announcement.THREE_CARDS, new Card(31))));
        when(player1.getTeam()).thenReturn(game.getTeams()[0]);
        when(player2.getTeam()).thenReturn(game.getTeams()[0]);

        assertTrue(game.processAnnouncements());

        assertEquals(180, game.getTeams()[0].getScore());
        assertEquals(0, game.getTeams()[1].getScore());

        when(player1.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player2.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player3.getAnnouncements()).thenReturn(Collections.singletonList(new Announcement(Announcement.STOECK, null)));
        when(player4.getAnnouncements()).thenReturn(Collections.emptyList());
        when(player3.getTeam()).thenReturn(game.getTeams()[1]);

        assertTrue(game.processAnnouncements());

        assertEquals(180, game.getTeams()[0].getScore());
        assertEquals(40, game.getTeams()[1].getScore());
    }

    @Test
    void test_play_plie() throws PlayerLeftExpection, BrokenRuleException {
        Card.atout = Card.COLOR_SPADE;
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        when(player1.choosePartner()).thenReturn(2);

        var game = new GameController(0);
        game.setNoWait(true);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);
        game.pickTeamMates();

        when(player4.getTeam()).thenReturn(game.getTeams()[0]);

        when(player2.play()).thenReturn(new Card(Card.RANK_10, Card.COLOR_HEART));
        when(player3.play()).thenReturn(new Card(Card.RANK_DAME, Card.COLOR_HEART));
        when(player4.play()).thenReturn(new Card(Card.RANK_6, Card.COLOR_SPADE));
        when(player1.play()).thenReturn(new Card(Card.RANK_AS, Card.COLOR_HEART));

        var plie = game.playPlie(1);

        assertEquals(player4, plie.getOwner());
        assertEquals(48, plie.getScore());

        verify(player1, times(1)).collectPlie(player4);
        verify(player2, times(1)).collectPlie(player4);
        verify(player3, times(1)).collectPlie(player4);
        verify(player4, times(1)).collectPlie(player4);
    }

    @Test
    void test_connection_listener() throws IOException {
        var listener = new ConnectionListener(23107);
        var network = new ClientNetwork();

        listener.start();

        var info = network.connect("localhost", -1, "GC");
        assertEquals(ConnectionError.CONNECTION_SUCCESSFUL, info.error);
        assertEquals(0, info.playerId);

        info = network.connect("localhost", 1234, "Pierre");
        assertEquals(ConnectionError.UNKNOWN_GAME, info.error);

        listener.terminate();
        try {
            listener.join(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

