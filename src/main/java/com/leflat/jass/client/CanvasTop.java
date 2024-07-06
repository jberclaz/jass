/*
 * CanvasTop.java
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

import static java.lang.Integer.max;

public class CanvasTop extends JassCanvas {
    private static final int X_STEP = 35;
    private static final int NAME_X = 30;
    private static final int NAME_Y = 15;
    private static final int DOT_SIZE = 7;
    private static final int CARD_Y = 20;

    // Constructeur
    public CanvasTop() {
    }

    @Override
    public Rectangle getNameArea() {
        var fontMetrics = getGraphics().getFontMetrics();
        int width = fontMetrics.stringWidth(name);
        return new Rectangle(NAME_X, max(0, NAME_Y - fontMetrics.getHeight()),
                width + 8 + DOT_SIZE * 2, fontMetrics.getHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();

        int w = (d.width - Card.DECK_SIZE - hand.size() * X_STEP) / 2;
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getImage(hand.get(i)), w + X_STEP * i, CARD_Y, this);
        }

        g2.drawString(name, NAME_X, NAME_Y);
        if (selectAtout) {
            int width = g.getFontMetrics().stringWidth(name);
            g2.fillOval(NAME_X + 8 + width, 7, DOT_SIZE, DOT_SIZE);
        }
    }
}
