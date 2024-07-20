import com.leflat.jass.client.JassPlayer;
import com.leflat.jass.server.ArtificialPlayer;
import com.leflat.jass.server.PlayerLeftExpection;
import com.leflat.jass.test.MockUi;
import com.leflat.jass.server.GameController;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FunctionalTest {
    @Test
    void functional_test() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        var player1 = new JassPlayer(new MockUi(0, 1), 0, "Berte", 0);
        var player2 = new JassPlayer(new MockUi(0, 1), 1, "GC", 0);
        var player3 = new JassPlayer(new MockUi(0, 1), 2, "Pischus", 0);
        var player4 = new JassPlayer(new MockUi(0, 1), 3, "Wein", 0);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        Assertions.assertTrue(game.isGameFull());

        assertDoesNotThrow(() -> {
            game.start();
            game.join();
        });
    }

    @Test
    void functional_test_with_artificial_player() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        var player1 = new JassPlayer(new MockUi(0, 1), 0, "Berte", 0);
        var player2 = new JassPlayer(new MockUi(0, 1), 1, "GC", 0);
        var player3 = new ArtificialPlayer(2, "iWein");
        var player4 = new ArtificialPlayer(3, "iPischus");

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        Assertions.assertTrue(game.isGameFull());

        game.setNoWait(true);

        assertDoesNotThrow(() -> {
            game.start();
            game.join();
        });
    }


}
