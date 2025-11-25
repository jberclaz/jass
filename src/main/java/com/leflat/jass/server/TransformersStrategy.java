package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.IJassStrategy;
import com.leflat.jass.common.Plie;

import java.util.List;

public class TransformersStrategy implements IJassStrategy {
    private final JassModelLoader modelLoader;
    private final IJassStrategy atoutFallbackPolicy; // For choosing atout

    public TransformersStrategy() {
        this("cp:/model/jassformer.onnx", new MonteCarloStrategy(1000));
    }

    public TransformersStrategy(String modelPath) {
        this(modelPath, new MonteCarloStrategy(1000));
    }

    public TransformersStrategy(String modelPath, IJassStrategy atoutFallbackPolicy) {
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

        byte[] tokens = gameView.getTransformersTokensForCardChoice();
        float[] logits = modelLoader.predict(tokens);
        Card bestCard = modelLoader.chooseBestCard(logits, validCards);

        // Name is not easily accessible here, just log the choice
        ArtificialPlayer.LOGGER.info("(Transformer) : chose " + bestCard);
        return bestCard;
    }

    @Override
    public int chooseTrumpSuit(boolean first, List<Card> hand, GameView gameView) {
        if (modelLoader == null) {
            // Fallback to the atout policy (which is MC) if NN fails
            ArtificialPlayer.LOGGER.warning("No NN model loaded: falling back on policy");
            return atoutFallbackPolicy.chooseTrumpSuit(first, hand, gameView);
        }

        byte[] tokens = gameView.getTransformersTokensForTrumpChoice(first);
        float[] logits = modelLoader.predict(tokens);
        int suit = modelLoader.chooseTrumpSuit(logits, first);

        // Name is not easily accessible here, just log the choice
        ArtificialPlayer.LOGGER.info("(Transformer) : chose trump " + suit);
        return suit;
   }
}