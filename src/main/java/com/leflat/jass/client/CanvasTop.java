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

import java.awt.*;

public class CanvasTop extends JassCanvas {
    private static final int X_STEP = 35;

    // Constructeur
    public CanvasTop() {
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();

        int w = (d.width - 36 - hand.size() * X_STEP) / 2;
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getInstance().getImage(hand.get(i)), w + X_STEP * i, 20, this);
        }

        g2.drawString(name, 30, 15);
        if (atout) {
            int width = g.getFontMetrics().stringWidth(name);
            g2.fillOval(38 + width, 7, 7, 7);
        }
    }
}
