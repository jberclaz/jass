package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DealSeedTests {
    private static GameController newGame(long seed, List<ArtificialPlayer> players) throws PlayerLeftExpection {
        var game = new GameController(0);
        game.setDealSeed(seed);
        for (int i = 0; i < 4; i++) {
            var player = new ArtificialPlayer(i, "p" + i, new RandomStrategy(), true);
            players.add(player);
            game.addPlayer(player);
        }
        return game;
    }

    private static List<List<Card>> hands(List<ArtificialPlayer> players) {
        return players.stream().map(p -> List.copyOf(p.getHand())).toList();
    }

    @Test
    void same_seed_deals_the_same_cards_to_the_same_seats() throws PlayerLeftExpection {
        List<ArtificialPlayer> playersA = new ArrayList<>(), playersB = new ArrayList<>();
        var gameA = newGame(42, playersA);
        var gameB = newGame(42, playersB);

        // game 3, hands 1 and 2, whatever happened before
        gameA.seedDeals(3);
        gameB.seedDeals(3);
        for (int hand = 0; hand < 2; hand++) {
            assertEquals(gameA.drawCards(), gameB.drawCards());
            assertEquals(hands(playersA), hands(playersB));
        }

        // another game gets other cards
        gameA.seedDeals(3);
        gameA.drawCards();
        gameB.seedDeals(4);
        gameB.drawCards();
        assertNotEquals(hands(playersA), hands(playersB));
    }
}
