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

    public void paint(Graphics g) {
        Dimension d = getSize();

        int w = (d.width - 36 - hand.size() * X_STEP) / 2;
        for (int i = 0; i < hand.size(); i++) {
            g.drawImage(CardImages.getInstance().getBackImage(), w + X_STEP * i, 20, this);
        }

        g.drawString(name, 30, 15);
        if (atout) {
            g.drawString("atout", name.length() * 7 + 60, 15);
        }
    }
}
