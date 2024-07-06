/*
 * CanvasCenter.java
 *
 * Created on 18. avril 2000, 18:04
 */


/**
 * @author Berclaz Jérôme
 * @version 1.1
 */

package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static java.lang.Integer.min;
import static java.lang.Math.floor;

public class CanvasCenter extends JPanel {
    private static final int X_STEP = 8;
    private static final int[] CARD_POS_X = {-35, 25, -35, -95};
    private static final int[] CARD_POS_Y = {2, -48, -98, -48};
    public static final int MODE_PASSIVE = 0;
    public static final int MODE_DRAW_TEAMS = 1;
    public static final int MODE_PICK_CARD = 2;
    public static final int MODE_GAME = 3;

    private int mode;  /* 0 : rien, 1 : tirer les équipes
     * 2 : tirer les équipes et choisir une carte
     * 3 : jouer */
    private final List<Integer> drawnCards = new ArrayList<>();
    private final Map<Integer, Card> shownCards = new HashMap<>();

    public CanvasCenter() {
        mode = MODE_PASSIVE;
        setDoubleBuffered(true);
    }

    public void setMode(int mode) {
        this.mode = mode;
        repaint();
    }

    public void drawCard(int index) {
        drawnCards.add(index);
        repaint(getCardArea(index));
    }

    public void resetCards() {
        drawnCards.clear();
        shownCards.clear();
        repaint();
    }

    public void showCard(Card card, int player) {
        shownCards.put(player, card);
        repaint(getPlayedCardArea(player));
    }

    public Collection<Card> getShownCards() {
        return shownCards.values();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Dimension d = getSize();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(51, 102, 0));
        g2.fillRoundRect(0, 0, d.width, d.height, 10, 10);

        switch (mode) {
            case MODE_DRAW_TEAMS:
            case MODE_PICK_CARD:
                int yOffset = (d.height - CardImages.IMG_HEIGHT) / 2;
                int xOffset = (d.width - 35 * X_STEP - CardImages.IMG_WIDTH) / 2;
                for (int i = 0; i < 36; i++) {
                    if (!drawnCards.contains(i)) {
                        g.drawImage(CardImages.getBackImage(), xOffset + i * X_STEP, yOffset, this);
                    }
                }
                break;
            case MODE_GAME:
                int w = d.width / 2;
                int h = d.height / 2;
                for (int i = 0; i < 4; i++) {
                    if (shownCards.containsKey(i)) {
                        g.drawImage(CardImages.getImage(shownCards.get(i)), w + CARD_POS_X[i], h + CARD_POS_Y[i], this);
                    }
                }
        }
    }

    private Rectangle getCardArea(int index) {
        Dimension d = getSize();
        int yOffset = (d.height - CardImages.IMG_HEIGHT) / 2;
        int xOffset = (d.width - 35 * X_STEP - CardImages.IMG_WIDTH) / 2;
        return new Rectangle(xOffset + index * X_STEP, yOffset, CardImages.IMG_WIDTH, CardImages.IMG_HEIGHT);
    }

    private Rectangle getPlayedCardArea(int index) {
        Dimension d = getSize();
        int w = d.width / 2;
        int h = d.height / 2;
        return new Rectangle(w + CARD_POS_X[index], h + CARD_POS_Y[index],CardImages.IMG_WIDTH, CardImages.IMG_HEIGHT);
    }

    public int getCardIndex(int x, int y) {
        if (mode != MODE_PICK_CARD) {
            return -1;
        }
        Dimension d = getSize();
        int yOffset = (d.height - CardImages.IMG_HEIGHT) / 2;
        int xOffset = (d.width - 35 * X_STEP - CardImages.IMG_WIDTH) / 2;
        if (y < yOffset || y > yOffset + CardImages.IMG_HEIGHT) {
            return -1;
        }
        int highCardNbr = (int) floor((x - xOffset) / (float) X_STEP);
        int lowCardNbr = (int) floor((x - xOffset - CardImages.IMG_WIDTH) / (float) X_STEP);
        if (highCardNbr < 0 || lowCardNbr > 35) {
            return -1;
        }
        for (int nbr = min(highCardNbr, 35); nbr >= lowCardNbr; nbr--) {
            if (!drawnCards.contains(nbr)) {
                return nbr;
            }
        }
        return -1;
    }
}
