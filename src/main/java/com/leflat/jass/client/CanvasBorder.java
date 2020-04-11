/*
 * CanvasBorder.java
 *
 * Created on 18. avril 2000, 16:49
 */


/**
 * @author Berclaz Jérôme
 * @version
 */

package com.leflat.jass.client;

import java.awt.*;

public class CanvasBorder extends JassCanvas {
    public static final int Y_STEP = 30;

    public CanvasBorder() {
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();

        g2.clearRect(0, 0, d.width, d.height);

        int w = (d.width - CardImages.IMG_WIDTH) / 2;
        int h = (d.height - hand.size() * Y_STEP - 66) / 2 + 20;
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getInstance().getImage(hand.get(i)), w, h + i * 30, this);
        }

        g2.drawString(name, 20, 30);
        if (atout) {
            g2.fillOval(20, 37, 7, 7);
        }
    }
}
