import com.leflat.jass.client.InteractivePlayer;
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

        var player1 = new InteractivePlayer(new MockUi(0, 1), 0, "Berte", 0);
        var player2 = new InteractivePlayer(new MockUi(0, 1), 1, "GC", 0);
        var player3 = new InteractivePlayer(new MockUi(0, 1), 2, "Pischus", 0);
        var player4 = new InteractivePlayer(new MockUi(0, 1), 3, "Wein", 0);

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
        var player1 = new InteractivePlayer(new MockUi(0, 1), 0, "Berte", 0);
        var player2 = new InteractivePlayer(new MockUi(0, 1), 1, "GC", 0);
        var player3 = new InteractivePlayer(new MockUi(0, 1), 2, "Wein", 0);
        var player4 = new ArtificialPlayer(3, "iPischus");

        var game = new GameController(0);
        game.setNoWait(true);
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


}
