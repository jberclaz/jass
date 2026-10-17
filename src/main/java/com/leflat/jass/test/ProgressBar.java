package com.leflat.jass.test;

import java.io.PrintStream;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-line console progress bar for the command-line tools, e.g.
 * {@code [##########..........]  10/20 games | 1:02 (ETA 1:02) | MonteCarlo-100 4 : 6 transformers}
 */
class ProgressBar {
    private static final int WIDTH = 30;
    // keeps a strong reference: java.util.logging only keeps weak references to loggers, which would lose the level
    private static final Logger APP_LOGGER = Logger.getLogger("com.leflat.jass");

    private final int total;
    private final String unit;
    private final long startNanos = System.nanoTime();
    private final PrintStream out = System.err;

    ProgressBar(int total, String unit) {
        this.total = total;
        this.unit = unit;
    }

    /** Hides the per-move and per-game INFO logs of the game, which would break the progress bar. */
    static void hideInfoLogs() {
        APP_LOGGER.setLevel(Level.WARNING);
    }

    void update(int done, String status) {
        int filled = total > 0 ? (int) ((long) WIDTH * done / total) : WIDTH;
        long elapsed = System.nanoTime() - startNanos;
        String eta = done > 0 && done < total ? " (ETA " + format(elapsed / done * (total - done)) + ")" : "";
        int digits = String.valueOf(total).length();
        // trailing spaces erase what is left of a longer previous line (e.g. when the ETA gets shorter)
        out.printf("\r[%s%s] %" + digits + "d/%d %s | %s%s%s   ",
                "#".repeat(filled), ".".repeat(WIDTH - filled), done, total, unit, format(elapsed), eta,
                status.isEmpty() ? "" : " | " + status);
        if (done >= total) {
            out.println();
        }
        out.flush();
    }

    private static String format(long nanos) {
        var d = Duration.ofNanos(nanos);
        return d.toHours() > 0
                ? String.format("%d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart())
                : String.format("%d:%02d", d.toMinutesPart(), d.toSecondsPart());
    }
}
