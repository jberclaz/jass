/*
 * CanvasPlayer.java
 *
 * Created on 18. avril 2000, 17:31
 */

/**
 * @author Berclaz Jérôme
 * @version
 */

package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import java.awt.*;

public class CanvasPlayer extends JassCanvas {
    private static final int X_STEP = 35;

    public CanvasPlayer() {
    }

    public void paint(Graphics g) {
        if (!hand.isEmpty()) {
            Dimension d = getSize();
            int cardsWidth = getCardsWidth();
            int xOffset = (d.width - cardsWidth) / 2;
            for (int i = 0; i < hand.size(); i++) {
                g.drawImage(CardImages.getInstance().getImage(hand.get(i)),
                        xOffset + i * X_STEP, 20, this);
            }
        }
        g.drawString(name, 30, 15);
        if (atout) {
            g.drawString("atout", name.length() * 7 + 60, 15);
        }
    }

    public Card getCard(int x, int y) {
        if (y < 20) {
            return null;
        }
        Dimension d = getSize();
        int cardsWidth = getCardsWidth();
        int xOffset = (d.width - cardsWidth) / 2;
        for (int i = hand.size() - 1; i >= 0; i--) {
            if (x >= xOffset + i * X_STEP && x < xOffset + i * X_STEP + CardImages.IMG_WIDTH) {
                return hand.get(i);
            }
        }
        return null;
    }

    private int getCardsWidth() {
        return CardImages.IMG_WIDTH + (hand.size() - 1) * X_STEP;
    }
}
