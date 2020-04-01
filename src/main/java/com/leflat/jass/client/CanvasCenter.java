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
    private int mode;        // 0 : rien, 1 : tirer les équipes
    // 2 : tirer les équipes et choisir une carte
    // 3 : jouer
    private List<Integer> drawnCards = new ArrayList<>();
    private Map<Integer, Card> shownCards = new HashMap<>();

    public CanvasCenter() {
        mode = 0;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void drawCard(int i) {
        drawnCards.add(i);
    }

    public void resetCards() {
        drawnCards.clear();
        shownCards.clear();
    }

    public void showCard(Card card, int player) {
        shownCards.put(player, card);
    }

    public Collection<Card> getShownCards() {
        return shownCards.values();
    }

    public void paint(Graphics g) {
        // System.out.println("Repaint");
        Dimension d = getSize();
        switch (mode) {
            case 1:
            case 2:
                for (int i = 0; i < 36; i++) {
                    if (!drawnCards.contains(i)) {
                        g.drawImage(CardImages.getInstance().getBackImage(), X_OFFSET + i * X_STEP, Y_OFFSET, this);
                    }
                }
                break;
            case 3:
                int w = d.width / 2;
                int h = d.height / 2;
                if (shownCards.containsKey(0))       // joueur
                    g.drawImage(CardImages.getInstance().getImage(shownCards.get(0)), w - 35, h + 2, 71, 96, this);
                if (shownCards.containsKey(1))
                    g.drawImage(CardImages.getInstance().getImage(shownCards.get(1)), w + 25, h - 48, 71, 96, this);
                if (shownCards.containsKey(2))       // haut
                    g.drawImage(CardImages.getInstance().getImage(shownCards.get(2)), w - 35, h - 98, 71, 96, this);
                if (shownCards.containsKey(3))       // droite
                    g.drawImage(CardImages.getInstance().getImage(shownCards.get(3)), w - 95, h - 48, 71, 96, this);
        }
    }

    public int getCard(int x, int y) {
        if (mode != 2) {
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
