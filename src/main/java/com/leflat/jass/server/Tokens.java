package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.PlayerPosition;

public class Tokens {
    // ===================================================================
// FINAL VOCABULARY — 128 tokens total (perfect for embedding layer)
// ===================================================================
    public static final int VOCAB_SIZE = 128;

    // ── Special tokens ─────────────────────────────────────
    public static final int PAD = 0;
    public static final int CLS = 1;
    public static final int SEP = 2;

    // ── Section markers (ONE per section) ──────────────────
    public static final int SECTION_GLOBALS = 3;
    public static final int SECTION_HAND = 4;
    public static final int SECTION_TRICK = 5;
    public static final int SECTION_HISTORY = 6;
    public static final int SECTION_BELIEF = 7;

    // ── Cards: 36 tokens → 10..45 ──────────────────────────
    public static int cardToken(Card c) {
        if (c == null) return PAD;
        return 10 + c.getNumber(); // 10..45
    }

    public static int cardToken(Integer c) {
        return 10 + c;
    }

    // ── Trump declaration signal ──────────────────────────
    public static final int TRUMP_FIRST_CHOICE = 50;  // Chose on first turn → STRONG
    public static final int TRUMP_FORCED = 51;  // Passed to partner → WEAK forehand

    // ── Player tokens (reused everywhere) ─────────────────

    public static final int P_SELF = 52;
    public static final int P_PARTNER = 53;
    public static final int P_OPP_L = 54;
    public static final int P_OPP_R = 55;
    public static int positionToken(PlayerPosition pos) {
        switch (pos.getCode()) {
            case 0: return P_SELF;
            case 2: return P_PARTNER;
            case 1: return P_OPP_R;
            case 3: return P_OPP_L;
            default: return PAD;
        }
    }

    // ── Trump suit (repeated 4×) → 56..59 ──────────────────
    public static int trumpToken(int color) { // color: 0=clubs,1=spades,2=hearts,3=diamonds
        return 56 + color;
    }

    // ── Score buckets (shared) ───────────────────────────
    public static int scoreToken(int score) {
        return 60 + Math.min(score / 10, 25);        // 0..257 → 60..85
    }

    public static int globalScoreToken(int score) {
        return 86 + Math.min(score / 100, 25);       // 0..2500+ → 86..111
    }

    // ── Confidence levels 0.0..1.0 → 112..122 ─────────────
    public static int confidenceToken(double prob) {
        int level = (int) Math.round(prob * 10);
        return 112 + Math.min(Math.max(level, 0), 10);
    }

    // ── Trick state ───────────────────────────────────────
    public static final int WIN_OUR_TEAM = 123;
    public static final int WIN_OPP_TEAM = 124;

    public static final int CHOOSE_NEXT_CARD = 125;
    public static final int CHOOSE_TRUMP_SUIT = 126;
}
