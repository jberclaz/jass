import com.leflat.jass.common.*;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.RemotePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class MockNetwork implements INetwork {
    public List<List<String>> sendParameters = new ArrayList<>();
    private Queue<String[]> answers = new LinkedBlockingQueue<>();

    public MockNetwork() {

    }

    public MockNetwork(String[] answer) {
        addAnswer(answer);
    }

    public void addAnswer(String[] message) {
        answers.add(message);
    }

    public void clearParameters() {
        sendParameters.clear();
    }

    @Override
    public int sendMessage(String message) {
        sendParameters.add(Collections.singletonList(message));
        return 0;
    }

    @Override
    public void setPlayerId(int id) {

    }

    @Override
    public int sendMessage(List<String> message) {
        sendParameters.add(message);
        return 0;
    }

    @Override
    public String[] receiveMessage() {
        if (answers.isEmpty()) {
            return new String[]{};
        }
        return answers.remove();
    }
}

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
            p1 = new RemotePlayer(1, new MockNetwork(new String[]{"GC"}));
            p2 = new RemotePlayer(2, new MockNetwork(new String[]{"Pischus"}));
            p3 = new RemotePlayer(3, new MockNetwork(new String[]{"Berte"}));
            p4 = new RemotePlayer(4, new MockNetwork(new String[]{"Wein"}));
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
            p1 = new RemotePlayer(1, new MockNetwork(new String[]{"GC"}));
            p2 = new RemotePlayer(2, new MockNetwork(new String[]{"Pischus"}));
            p3 = new RemotePlayer(3, new MockNetwork(new String[]{"Berte"}));
            p4 = new RemotePlayer(4, new MockNetwork(new String[]{"Wein"}));
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
        List<MockNetwork> networks = new ArrayList<>();
        var game = new GameController(0);
        try {
            for (int i = 0; i < 4; i++) {
                networks.add(new MockNetwork(new String[]{"Wein"}));
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
                var player = new RemotePlayer(i, new MockNetwork(new String[]{"Hhip"}));
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
}

