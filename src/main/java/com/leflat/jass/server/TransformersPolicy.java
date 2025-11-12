package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.IJassPolicy;
import com.leflat.jass.common.Plie;

import java.util.List;

public class TransformersPolicy implements IJassPolicy {
    private final JassModelLoader modelLoader;
    private final IJassPolicy atoutFallbackPolicy; // For choosing atout

    public TransformersPolicy(String modelPath, IJassPolicy atoutFallbackPolicy) {
        JassModelLoader loader = null;
        try {
            loader = new JassModelLoader(modelPath);
        } catch (Exception e) {
            ArtificialPlayer.LOGGER.severe("Failed to load JassFormer model: " + e.getMessage());
        }
        this.modelLoader = loader;
        this.atoutFallbackPolicy = atoutFallbackPolicy;
    }

    @Override
    public Card chooseCard(List<Card> validCards,
                           GameView gameView,
                           Plie currentPlie,
                           List<Card> hand,
                           int pliesWon) {

        if (modelLoader == null) {
            // Fallback to the atout policy (which is MC) if NN fails
            ArtificialPlayer.LOGGER.warning("No NN model loaded: falling back on policy");
            return atoutFallbackPolicy.chooseCard(validCards, gameView, currentPlie, hand, pliesWon);
        }

        byte[] tokens = gameView.encodeStateForTransformer();
        float[] logits = modelLoader.predict(tokens);
        Card bestCard = modelLoader.chooseBestCard(logits, validCards);

        // Name is not easily accessible here, just log the choice
        ArtificialPlayer.LOGGER.info("(Transformer) : chose " + bestCard);
        return bestCard;
    }

    @Override
    public int chooseTrumpSuit(boolean first, List<Card> hand, GameView gameView) {
        // NN is not trained for this; delegate to the fallback policy (MC)
        return atoutFallbackPolicy.chooseTrumpSuit(first, hand, gameView);
    }
}