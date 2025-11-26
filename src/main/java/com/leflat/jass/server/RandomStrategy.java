package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.common.Plie;

import java.util.List;
import java.util.Random;

public class RandomStrategy implements IJassStrategy {
    private final Random rand = new Random();

    public String toString() { return "random"; }

    @Override
    public Card chooseCard(List<Card> validCards, GameView gameView, Plie currentPlie, List<Card> hand,  int pliesWonByTeam) {
        return validCards.get(rand.nextInt(validCards.size()));
    }

    @Override
    public int chooseTrumpSuit(boolean first, List<Card> hand, GameView gameView) {
        return rand.nextInt(first ? 5 : 4);
    }
}