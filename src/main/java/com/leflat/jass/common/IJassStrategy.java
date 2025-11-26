package com.leflat.jass.common;

import com.leflat.jass.server.GameView;

import java.util.List;

/**
 * An interface representing a card-playing strategy (policy)
 * for an ArtificialPlayer.
 */
public interface IJassStrategy {
    /**
     * Chooses a card to play.
     *
     * @param validCards The list of legal cards to play.
     * @param gameView   The current view of the game state.
     * @param currentPlie The current trick being played.
     * @param hand       The player's current hand.
     * @return The chosen Card.
     */
    Card chooseCard(List<Card> validCards,
                    GameView gameView,
                    Plie currentPlie,
                    List<Card> hand,
                    int pliesWonByTeam);

    /**
     * Chooses a trump suit.
     *
     * @param first      True if the player is the first to choose.
     * @param hand       The player's current hand.
     * @param gameView   The current view of the game state (before trump).
     * @return The chosen suit (Card.COLOR_*) or Card.COLOR_NONE to pass.
     */
    int chooseTrumpSuit(boolean first,
                    List<Card> hand, GameView gameView);

    String toString();
}