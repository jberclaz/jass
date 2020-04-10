import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.server.GameController;
import com.leflat.jass.server.PlayerLeftExpection;
import org.junit.jupiter.api.Test;

public class FunctionalTest {
    @Test
    void functional_test() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        var clientNetworkFactory = new MockNetworkFactory();
        var player1 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        var player2 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        var player3 = new JassPlayer(clientNetworkFactory, new MockUiFactory());
        var player4 = new JassPlayer(clientNetworkFactory, new MockUiFactory());

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        game.setNoWait(true);

        game.run();
    }
}
