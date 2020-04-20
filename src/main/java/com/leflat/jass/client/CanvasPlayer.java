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

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.List;

import static java.lang.Integer.max;
import static java.lang.Math.min;

public class CanvasPlayer extends JassCanvas implements MouseMotionListener {
    private static final int X_STEP = 35;
    private static final int CARD_Y = 20;
    private static final int NAME_X = 30;
    private static final int NAME_Y = 15;
    private static final int DOT_SIZE = 7;
    private int hoveredCard = -1;

    public CanvasPlayer() {
        addMouseMotionListener(this);
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

        if (!hand.isEmpty()) {
            int cardsWidth = getCardsWidth();
            int xOffset = (d.width - cardsWidth) / 2;
            for (int i = 0; i < hand.size(); i++) {
                if (i == hoveredCard) {
                    AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC, 0.7f);
                    g2.setComposite(ac);
                }
                g2.drawImage(CardImages.getImage(hand.get(i)), xOffset + i * X_STEP, CARD_Y, this);
                if (i == hoveredCard) {
                    AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);
                    g2.setComposite(ac);
                }
            }
        }

        g2.drawString(name, NAME_X, NAME_Y);
        if (selectAtout) {
            int width = g.getFontMetrics().stringWidth(name);
            g2.fillOval(NAME_X + 8 + width, NAME_Y - 8, DOT_SIZE, DOT_SIZE);
        }
    }

    public int getCardIndex(int x, int y) {
        if (mode != JassCanvas.MODE_PLAY) {
            return -1;
        }
        if (y < CARD_Y || y >= CARD_Y + CardImages.IMG_HEIGHT) {
            return -1;
        }
        Dimension d = getSize();
        int cardsWidth = getCardsWidth();
        int xOffset = (d.width - cardsWidth) / 2;
        if (x < xOffset || x >= xOffset + cardsWidth) {
            return -1;
        }
        int cardNbr = (int) Math.floor((x - xOffset) / (float) X_STEP);
        return min(hand.size() - 1, cardNbr);
    }

    public Card getCard(int x, int y) {
        int index = getCardIndex(x, y);
        if (index < 0) {
            return null;
        }
        return hand.get(index);
    }

    private int getCardsWidth() {
        return CardImages.IMG_WIDTH + (hand.size() - 1) * X_STEP;
    }

    public List<Card> getHand() {
        return hand;
    }

    public Rectangle getCardArea(int index) {
        Dimension d = getSize();
        int cardsWidth = getCardsWidth();
        int xOffset = (d.width - cardsWidth) / 2;
        return new Rectangle(xOffset + index * X_STEP, CARD_Y, CardImages.IMG_WIDTH, CardImages.IMG_HEIGHT);
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        var index = getCardIndex(mouseEvent.getX(), mouseEvent.getY());
        if (index < 0) {
            if (hoveredCard != -1) {
                repaint(getCardArea(hoveredCard));
            }
            hoveredCard = -1;
            return;
        }
        if (index != hoveredCard) {
            var area = getCardArea(index);
            if (hoveredCard >= 0) {
                area = area.union(getCardArea(hoveredCard));
            }
            hoveredCard = index;
            repaint(area);
        }
    }
}
