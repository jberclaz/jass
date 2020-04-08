import com.leflat.jass.common.RemoteCommand;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.server.RemotePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemotePlayerTests {
    private MockNetwork network;
    private RemotePlayer player;

    @BeforeEach
    public void setUp() {
        network = new MockNetwork();
        network.addAnswer(new String[] { "GC" });
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
        network.addAnswer(new String[] {"RANDOM"});
        player.chooseTeamSelectionMethod();
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.CHOOSE_TEAM_SELECTION_METHOD, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }

    @Test
    public void prepare_team_drwaing_test() throws PlayerLeftExpection {
        player.prepareTeamDrawing(true);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.PREPARE_TEAM_DRAWING, Integer.parseInt(network.sendParameters.get(0).get(0)));
        network.clearParameters();
        player.prepareTeamDrawing(false);
        assertEquals(1, network.sendParameters.size());
        assertEquals(RemoteCommand.RESTART_TEAM_DRAWING, Integer.parseInt(network.sendParameters.get(0).get(0)));
    }
}
