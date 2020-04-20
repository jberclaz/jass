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
    private static final int NAME_X = 20;
    private static final int NAME_Y = 30;
    private static final int DOT_SIZE = 7;

    public CanvasBorder() {
    }

    @Override
    public Rectangle getNameArea() {
        var fontMetrics = getGraphics().getFontMetrics();
        int width = fontMetrics.stringWidth(name);
        return new Rectangle(NAME_X, NAME_Y - fontMetrics.getHeight(),
                width, fontMetrics.getHeight() + 7 + 2 * DOT_SIZE);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();

        int w = (d.width - CardImages.IMG_WIDTH) / 2;
        int h = (d.height - hand.size() * Y_STEP - 66) / 2 + 20;
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getImage(hand.get(i)), w, h + i * Y_STEP, this);
        }

        g2.drawString(name, NAME_X, NAME_Y);
        if (selectAtout) {
            g2.fillOval(NAME_X, NAME_Y + 7, DOT_SIZE, DOT_SIZE);
        }
    }
}
