import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.client.RemoteController;
import com.leflat.jass.client.ServerDisconnectedException;
import com.leflat.jass.common.*;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.RemotePlayer;
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
                var result =  remotePlayer.getAnnouncements();
                assertEquals(anouncement.size(), result.size());
                assertEquals(anouncement.get(0), result.get(0));
                remotePlayer.setAnnouncements(otherPlayer, anouncement);
                remotePlayer.setGameResult(team);
                assertTrue(remotePlayer.getNewGame());
                remotePlayer.playerLeft(otherPlayer);
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
        verify(mockedPlayer, times(1)).getNewGame();
        verify(mockedPlayer, times(1)).playerLeft(otherPlayer);
    }
}

