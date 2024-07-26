import com.leflat.jass.client.InteractivePlayer;
import com.leflat.jass.server.*;
import com.leflat.jass.test.MockUi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


public class FunctionalTest {
    private static final String[] names = {"Berte", "GC", "Pischus", "Wein"};

    @Test
    void functional_test() throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setNoWait(true);

        for (int i = 0; i < 4; ++i) {
            var player = new InteractivePlayer(new MockUi(0, 1), i, names[i], 0);
            game.addPlayer(player);
        }
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

        for (int i = 0; i < 3; ++i) {
            game.addPlayer(new InteractivePlayer(new MockUi(0, 1), i, names[i], 0));
        }
        game.addPlayer(new ArtificialPlayer(3, "iPischus", 10));

        Assertions.assertTrue(game.isGameFull());

        assertDoesNotThrow(() -> {
            game.start();
            game.join();
        });
    }
}
