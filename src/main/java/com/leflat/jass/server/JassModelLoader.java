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
    private OrtEnvironment env;
    private OrtSession session;

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

    public float[] predict(byte[] tokens) {
        if (tokens.length != 95) {
            throw new IllegalArgumentException("Tokens must be exactly 95 bytes, got " + tokens.length);
        }
        try {
            // Convert byte[] to float[] for ONNX (int64 input as float32 for simplicity)
            long[] tokenLongs = new long[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                tokenLongs[i] = tokens[i] & 0xFF;
            }
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenLongs), new long[]{1, 95});

            // Run inference
            OrtSession.Result result = session.run(Collections.singletonMap("tokens", inputTensor));
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            float[] logits = outputTensor.getFloatBuffer().array();

            inputTensor.close();
            result.close();
            return logits;  // [36]
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
}