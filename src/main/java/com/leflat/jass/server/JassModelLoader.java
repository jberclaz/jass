package com.leflat.jass.server;

import ai.onnxruntime.*;
import com.leflat.jass.common.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.LongBuffer;
import java.util.Collections;
import java.util.List;

public class JassModelLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(JassModelLoader.class);
    private final OrtEnvironment env;
    private final OrtSession session;
    private static final int TOKEN_LENGTH = 96;
    private static final String CARD_OUTPUT_NAME = "logits";
    private static final String TRUMP_OUTPUT_NAME = "log_softmax_1";

    public JassModelLoader(String modelPath) {
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                throw new RuntimeException("Model file not found: " + modelPath);
            }
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
            LOGGER.info("Loaded JassFormer ONNX model from {} with {} inputs", modelPath, session.getInputInfo().size());
        } catch (OrtException e) {
            LOGGER.error("Failed to load ONNX model from {}", modelPath, e);
            throw new RuntimeException("ONNX load failed — check model file and version", e);
        }
    }

    /**
     * Runs inference on the given tokens and returns the appropriate logit array
     * based on the game phase.
     *
     * @param tokens The raw byte array of game state tokens.
     * @return A float[] array of logits. This will be size 36 for the card-play phase
     * (token[2] == 125) or size 5 for the trump-choice phase (token[2] == 126).
     */
    public float[] predict(byte[] tokens) {
        if (tokens.length != TOKEN_LENGTH) {
            throw new IllegalArgumentException("Tokens must be exactly " + TOKEN_LENGTH + " bytes, got " + tokens.length);
        }

        // 1. Convert tokens to long[] and read the phase
        long[] tokenLongs = new long[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            tokenLongs[i] = tokens[i] & 0xFF;
        }

        // 2. Determine the phase
        long phase = tokenLongs[2];

        // 3. Use try-with-resources for automatic resource management (safer)
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenLongs), new long[]{1, TOKEN_LENGTH});
             OrtSession.Result result = session.run(Collections.singletonMap("tokens", inputTensor))) {

            if (phase == 125) {
                // --- Card Play Phase ---
                // Fetch the card logits output by its name
                OnnxTensor cardOutputTensor = (OnnxTensor) result.get(CARD_OUTPUT_NAME)
                        .orElseThrow(() -> new RuntimeException("ONNX model did not return expected output: " + CARD_OUTPUT_NAME));

                // Return the float array of size [36]
                return cardOutputTensor.getFloatBuffer().array();

            } else if (phase == 126) {
                // --- Trump Choice Phase ---
                // Fetch the trump logits output by its name
                OnnxTensor trumpOutputTensor = (OnnxTensor) result.get(TRUMP_OUTPUT_NAME)
                        .orElseThrow(() -> new RuntimeException("ONNX model did not return expected output: " + TRUMP_OUTPUT_NAME));

                // Return the float array of size [5]
                return trumpOutputTensor.getFloatBuffer().array();

            } else {
                // Unknown phase
                throw new IllegalArgumentException("Unknown phase token at index 2: " + phase);
            }

        } catch (OrtException e) {
            LOGGER.error("Inference failed", e);
            throw new RuntimeException("ONNX inference error", e);
        }
    }

    public Card chooseBestCard(float[] logits, List<Card> legalCards) {
        if (legalCards.isEmpty()) {
            throw new IllegalStateException("No legal cards!");
        }
        int bestIdx = -1;
        float bestLogit = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < legalCards.size(); i++) {
            Card c = legalCards.get(i);
            int cardIdx = c.getColor() * 9 + c.getRank();  // 0-35
            float logit = logits[cardIdx];
            if (logit > bestLogit) {
                bestLogit = logit;
                bestIdx = i;
            }
        }
        return legalCards.get(bestIdx);
    }

    public int chooseTrumpSuit(float[] logits, boolean isFirstTurn) {
        if (logits.length != 5) {
            throw new IllegalArgumentException("Logits array must be of length 5, got " + logits.length);
        }

        int bestIndex = 0;
        float maxLogit = Float.NEGATIVE_INFINITY;

        // Determine the number of options to check
        // If first turn, check all 5 (including pass, index 4)
        // If second turn, check only first 4 (cannot pass)
        int numOptions = isFirstTurn ? 5 : 4;

        for (int i = 0; i < numOptions; i++) {
            if (logits[i] > maxLogit) {
                maxLogit = logits[i];
                bestIndex = i;
            }
        }

        return bestIndex;
    }

}