import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.INetwork;
import com.leflat.jass.common.RemoteCommand;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.RemotePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockNetwork implements INetwork {
    public List<List<String>> sendParameters = new ArrayList<>();

    @Override
    public int sendMessage(String message) {
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
        return new String[] {"name"};
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
            p1 = new RemotePlayer(1, new MockNetwork());
            p2 = new RemotePlayer(2, new MockNetwork());
            p3 = new RemotePlayer(3, new MockNetwork());
            p4 = new RemotePlayer(4, new MockNetwork());
            game.addPlayer(p2);
            game.addPlayer(p1);
            game.addPlayer(p4);
            game.addPlayer(p3);
        }
        catch (PlayerLeftExpection ignored) {
            return;
        }

        try {
            int position = (Integer)getPlayerPosition.invoke(game, p4);
            assertEquals(2, position);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
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
            p1 = new RemotePlayer(1, new MockNetwork());
            p2 = new RemotePlayer(2, new MockNetwork());
            p3 = new RemotePlayer(3, new MockNetwork());
            p4 = new RemotePlayer(4, new MockNetwork());
            game.addPlayer(p2);
            assertEquals(1, game.getNbrPlayers());
            assertFalse(game.isGameFull());
            game.addPlayer(p1);
            game.addPlayer(p4);
            game.addPlayer(p3);
            assertTrue(game.isGameFull());
        }
        catch (PlayerLeftExpection ignored) {
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
            for (int i=0; i<4; i++) {
                networks.add(new MockNetwork());
                players.add(new RemotePlayer(i, networks.get(i)));
                game.addPlayer(players.get(i));
            }
        }
        catch (PlayerLeftExpection ignored) {
            return;
        }

        try {
            int startingPlayer = (int)drawCards.invoke(game);
            for (int i=0; i<4; i++) {
                assertEquals(4, networks.get(i).sendParameters.size());
            }
            var parameters = networks.get(startingPlayer).sendParameters.get(3);
            assertEquals(parameters.size(), 10);
            assertEquals(RemoteCommand.SET_HAND, Integer.parseInt(parameters.get(0)));
            assertTrue(parameters.stream().anyMatch(p -> Integer.parseInt(p) == Card.DIAMOND_SEVEN));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}

