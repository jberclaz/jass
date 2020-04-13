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

import java.util.List;

public class CanvasPlayer extends JassCanvas {
    private static final int X_STEP = 35;

    public CanvasPlayer() {
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();

        g2.clearRect(0, 0, d.width, d.height);

        if (!hand.isEmpty()) {
            int cardsWidth = getCardsWidth();
            int xOffset = (d.width - cardsWidth) / 2;
            for (int i = 0; i < hand.size(); i++) {
                g2.drawImage(CardImages.getInstance().getImage(hand.get(i)),
                        xOffset + i * X_STEP, 20, this);
            }
        }

        g2.drawString(name, 30, 15);
        if (atout) {
            int width = g.getFontMetrics().stringWidth(name);
            g2.fillOval(38 + width, 7, 7, 7);
        }
    }

    public Card getCard(int x, int y) {
        if (mode != JassCanvas.MODE_PLAY) {
            return null;
        }
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

    public List<Card> getHand() {
        return hand;
    }
}
