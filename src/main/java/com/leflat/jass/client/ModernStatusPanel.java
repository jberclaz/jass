package com.leflat.jass.client;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;

/**
 * This class contains the information text box at the bottom of the UI.
 */
public class ModernStatusPanel extends JPanel {
    private String text = "";

    public ModernStatusPanel() {
        setBorder(new BevelBorder(BevelBorder.LOWERED));
    }

    public void displayMessage(String message) {
        text = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var size = getSize();

        if (text.isEmpty()) {
            return;
        }

        var fontMetrics = g2.getFontMetrics();
        int stringWidth = fontMetrics.stringWidth(text);
        int nbrLines = 1;
        String[] lines;
        if (stringWidth > size.width) {
            nbrLines = (int) Math.ceil(stringWidth / size.getWidth());
            lines = new String[nbrLines];
            int step = text.length() / nbrLines;
            for (int i = 0; i < nbrLines; i++) {
                lines[i] = text.substring(i * step, (i + 1) * step);
            }
        } else {
            lines = new String[1];
            lines[0] = text;
        }
        int stringHeight = fontMetrics.getHeight() * nbrLines + 5 * (nbrLines - 1);
        int stringY = (size.height - stringHeight) / 2 + fontMetrics.getHeight() / 2;
        int lineNbr = 0;
        for (var line : lines) {
            g2.drawString(line, 10, stringY + (fontMetrics.getHeight() + 5) * lineNbr);
            lineNbr++;
        }
    }
}
