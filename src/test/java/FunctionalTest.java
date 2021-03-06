import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.test.MockNetworkFactory;
import com.leflat.jass.test.MockUiFactory;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;

public class FunctionalTest {
    @Test
    void functional_test() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        var clientNetworkFactory = new MockNetworkFactory();
        var player1 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        player1.setName("Berte");
        var player2 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        player2.setName("GC");
        var player3 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        player3.setName("Pischus");
        var player4 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        player4.setName("Wein");

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        assertTrue(game.isGameFull());

        game.setNoWait(true);

        game.run();
    }
}
