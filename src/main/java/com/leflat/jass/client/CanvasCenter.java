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

import java.awt.*;
import java.util.*;
import java.util.List;

import static java.lang.Integer.min;

public class CanvasCenter extends Canvas {
    private static final int X_OFFSET = 10;
    private static final int Y_OFFSET = 40;
    private static final int X_STEP = 8;
    private static final int[] CARD_POS_X = {-35, 25, -35, -95};
    private static final int[] CARD_POS_Y = {2, -48, -98, -48};
    public static final int MODE_PASSIVE = 0;
    public static final int MODE_DRAW_TEAMS = 1;
    public static final int MODE_PICK_CARD = 2;
    public static final int MODE_GAME = 3;


    private int mode;        // 0 : rien, 1 : tirer les équipes
    // 2 : tirer les équipes et choisir une carte
    // 3 : jouer
    private List<Integer> drawnCards = new ArrayList<>();
    private Map<Integer, Card> shownCards = new HashMap<>();

    public CanvasCenter() {
        mode = MODE_PASSIVE;
    }

    public void setMode(int mode) {
        this.mode = mode;
        repaint();
    }

    public void drawCard(int i) {
        drawnCards.add(i);
        repaint();
    }

    public void resetCards() {
        drawnCards.clear();
        shownCards.clear();
        repaint();
    }

    public void showCard(Card card, int player) {
        shownCards.put(player, card);
        repaint();
    }

    public Collection<Card> getShownCards() {
        return shownCards.values();
    }

    public void paint(Graphics g) {
        System.out.println("Repaint");
        Dimension d = getSize();
        switch (mode) {
            case MODE_DRAW_TEAMS:
            case MODE_PICK_CARD:
                for (int i = 0; i < 36; i++) {
                    if (!drawnCards.contains(i)) {
                        g.drawImage(CardImages.getInstance().getBackImage(), X_OFFSET + i * X_STEP, Y_OFFSET, this);
                    }
                }
                break;
            case MODE_GAME:
                int w = d.width / 2;
                int h = d.height / 2;
                for (int i = 0; i < 4; i++) {
                    if (shownCards.containsKey(i)) {
                        g.drawImage(CardImages.getInstance().getImage(shownCards.get(i)), w + CARD_POS_X[i], h + CARD_POS_Y[i], this);
                    }
                }
        }
    }

    public int getCard(int x, int y) {
        if (mode != MODE_PICK_CARD) {
            return -1;
        }
        if (y < Y_OFFSET || y > Y_OFFSET + CardImages.IMG_HEIGHT) {
            return -1;
        }
        int highCardNbr = (x - X_OFFSET) / X_STEP;
        int lowCardNbr = (x - X_OFFSET - CardImages.IMG_WIDTH) / X_STEP;
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
