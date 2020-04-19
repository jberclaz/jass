package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.*;

public class ModernGamePanel extends JPanel {
    enum GameMode {
        TEAM_DRAWING, GAME
    }

    private final static Logger LOGGER = Logger.getLogger(OriginalUi.class.getName());
    private final Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private static final float ASPECT_RATIO = 630f / 565;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private GameMode gameMode;
    private final Set<Integer> drawnCards = new HashSet<>();
    private final Map<PlayerPosition, Card> playedCards = new HashMap<>();
    private final Map<PlayerPosition, Float> cardAngles = new HashMap<>();
    private final Random random = new Random();

    public ModernGamePanel() {
        super();
        gameMode = GameMode.TEAM_DRAWING;
        drawnCards.clear();
        setDoubleBuffered(true);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            LOGGER.log(Level.WARNING, "Unable to set look and feel", e);
        }
    }

    public void setPlayer(PlayerPosition position, BasePlayer player) {
        players.put(position, player);
        cardAngles.put(position, random.nextFloat() - 0.5f);
    }

    public void setHand(PlayerPosition position, List<Card> hand) {
        try {
            players.get(position).setHand(hand);
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
    }

    public void setPlayedCard(PlayerPosition position, Card card) {
        playedCards.put(position, card);
    }

    public void setMode(GameMode mode) {
        this.gameMode = mode;
    }

    public void drawCard(int cardPosition, Card card, PlayerPosition playerPosition) {
        drawnCards.add(cardPosition);
        try {
            players.get(playerPosition).setHand(Collections.singletonList(card));
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
    }

    public void removeCard(PlayerPosition playerPosition) {
        removeCard(playerPosition, 0);
    }

    public void removeCard(PlayerPosition playerPosition, int cardPosition) {
        players.get(playerPosition).getHand().remove(cardPosition);
    }

    public boolean isInsidePlayerCardsArea(int x, int y) {
        return getHandArea(getRenderingDimension()).contains(x, y);
    }

    public Card getCard(int x, int y) {
        var handArea = getHandArea(getRenderingDimension());
        if (!handArea.contains(x, y)) {
            return null;
        }
        var hand = players.get(PlayerPosition.MYSELF).getHand();
        var hand_size = hand.size();
        var card_width = round((float) handArea.height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);
        float card_x_step = (handArea.width - card_width) / (float) (hand_size - 1);
        int cardNumber = (int) floor((x - handArea.x) / card_x_step);
        return hand.get(max(cardNumber, hand_size - 1));
    }

    private Rectangle getHandArea(Rectangle renderingArea) {
        int card_height = renderingArea.height / 7;
        int hand_x_offset = renderingArea.x + renderingArea.width / 3;
        int hand_y_offset = renderingArea.y + renderingArea.height - card_height - renderingArea.height / 10;
        int hand_width = renderingArea.width / 3;
        return new Rectangle(hand_x_offset, hand_y_offset, hand_width, card_height);
    }

    private Rectangle getCardArea(int number, Rectangle area) {
        var handArea = getHandArea(area);
        Rectangle card = new Rectangle();
        card.width = round((float) handArea.height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);

        float card_x_step = (handArea.width - card.width) / (float) (players.get(PlayerPosition.MYSELF).getHand().size() - 1);
        card.x = handArea.x + round(number * card_x_step);
        card.y = handArea.y;
        return card;
    }

    private Rectangle getRenderingDimension() {
        Dimension d = getSize();
        var area = new Rectangle();
        if ((float) d.width / d.height > ASPECT_RATIO) {
            area.width = round(d.height * ASPECT_RATIO);
            area.height = d.height;
            area.x = (d.width - area.width) / 2;
            area.y = 0;
        } else {
            area.width = d.width;
            area.height = round(d.width / ASPECT_RATIO);
            area.x = 0;
            area.y = (d.height - area.height) / 2;
        }
        return area;
    }

    Rectangle getCenterArea(Rectangle renderingArea) {
        int carpet_width = round(renderingArea.width * 390f / 630f);
        int carpet_height = round(renderingArea.height * 210f / 565f);
        int carpet_x_offset = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int carpet_y_offset = renderingArea.y + round(renderingArea.height * 120f / 565f);
        return new Rectangle(carpet_x_offset, carpet_y_offset, carpet_width, carpet_height);
    }

    Rectangle getPlayerArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 390f / 630f);
        int height = round(renderingArea.height * 120f / 565f);
        int x = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int y = renderingArea.y + round(renderingArea.height * 330f / 565f);
        return new Rectangle(x, y, width, height);
    }

    Rectangle getTopArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 390f / 630f);
        int height = round(renderingArea.height * 120f / 565f);
        int x = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int y = renderingArea.y;
        return new Rectangle(x, y, width, height);
    }

    Rectangle paintCenter(Graphics2D g2, Rectangle renderingArea, int cardWidth, int cardHeight) {
        // draw carpet
        g2.setColor(CARPET_COLOR);
        var carpetArea = getCenterArea(renderingArea);
        g2.fillRoundRect(carpetArea.x, carpetArea.y, carpetArea.width, carpetArea.height, renderingArea.width / 40, renderingArea.width / 40);

        if (gameMode == GameMode.TEAM_DRAWING) {
            for (int i = 0; i < 36; i++) {
                int card_x_offset = carpetArea.width / 10;
                float card_x_step = (carpetArea.width - 2 * card_x_offset - cardWidth) / 35.0f;
                int card_y_offset = (carpetArea.height - cardHeight) / 2 + carpetArea.y;
                if (!drawnCards.contains(i)) {
                    g2.drawImage(CardImages.getBackImage(), round(carpetArea.x + card_x_offset + i * card_x_step),
                            card_y_offset, cardWidth, cardHeight, this);
                }
            }
        } else {
            for (var entry : playedCards.entrySet()) {
                switch (entry.getKey()) {
                    case MYSELF:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                carpetArea.x + (carpetArea.width - cardWidth) / 2,
                                carpetArea.y + (carpetArea.height - cardHeight) - cardHeight / 10,
                                cardWidth, cardHeight, this);
                        break;
                    case ACROSS:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                carpetArea.x + (carpetArea.width - cardWidth) / 2,
                                carpetArea.y + cardHeight / 10,
                                cardWidth, cardHeight, this);
                        break;
                    case LEFT:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                carpetArea.x + cardWidth,
                                carpetArea.y + (carpetArea.height - cardHeight) / 2,
                                cardWidth, cardHeight, this);
                        break;
                    case RIGHT:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                carpetArea.x + carpetArea.width - cardWidth * 2,
                                carpetArea.y + (carpetArea.height - cardHeight) / 2,
                                cardWidth, cardHeight, this);
                        break;
                }
            }
        }
        return carpetArea;
    }

    void paintPlayerArea(Graphics2D g2, Rectangle playerArea, int cardWidth, int cardHeight) {
        var player = players.get(PlayerPosition.MYSELF);
        var hand = player.getHand();
        int hand_width = round(playerArea.width * .8f);
        int hand_x_offset = playerArea.x + round(playerArea.width * .1f);
        int hand_y_offset = playerArea.y + round(playerArea.height * 20f / 120f);
        float card_x_step = (hand_width - cardWidth) / (float) (hand.size() - 1);
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getImage(hand.get(i)),
                    hand_x_offset + round(i * card_x_step), hand_y_offset,
                    cardWidth, cardHeight, this);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        var renderingArea = getRenderingDimension();

        int cardHeight = round(renderingArea.height * 96f / 565f);
        int cardWidth = round((float) cardHeight * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);

        var carpetArea = paintCenter(g2, renderingArea, cardWidth, cardHeight);

        // draw cards
        for (var entry : players.entrySet()) {
            switch (entry.getKey()) {
                case MYSELF:
                    paintPlayerArea(g2, getPlayerArea(renderingArea), cardWidth, cardHeight);
                    break;
                case ACROSS:
                    var hand = entry.getValue().getHand();
                    var hand_width = carpetArea.width;
                    var hand_x_offset = carpetArea.x;
                    var hand_y_offset = renderingArea.y + renderingArea.height / 10;
                    var card_x_step = (hand_width - cardWidth) / (float) (hand.size() - 1);
                    for (int i = hand.size() - 1; i >= 0; i--) {
                        g2.drawImage(CardImages.getImage(hand.get(i)),
                                hand_x_offset + round(i * card_x_step), hand_y_offset,
                                cardWidth, cardHeight, this);
                    }
                    break;
                case LEFT:
                    hand = entry.getValue().getHand();
                    float scale = (float) cardHeight / CardImages.IMG_HEIGHT;
                    int hand_height = carpetArea.height;
                    float card_y_step = (hand_height - cardWidth) / (float) (hand.size() - 1);
                    hand_x_offset = renderingArea.x + renderingArea.width / 6;
                    hand_y_offset = carpetArea.y;
                    for (int i = hand.size() - 1; i >= 0; i--) {
                        var xform = new AffineTransform();
                        xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getImage(hand.get(i)), xform);
                    }
                    break;
                case RIGHT:
                    hand = entry.getValue().getHand();
                    scale = (float) cardHeight / CardImages.IMG_HEIGHT;
                    hand_height = carpetArea.height;
                    card_y_step = (hand_height - cardWidth) / (float) (hand.size() - 1);
                    hand_x_offset = renderingArea.x + renderingArea.width - renderingArea.width / 6 - cardHeight;
                    hand_y_offset = carpetArea.y;
                    for (int i = 0; i < hand.size(); i++) {
                        var xform = new AffineTransform();
                        xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getImage(hand.get(i)), xform);
                    }
                    break;
            }
        }

        // draw names
        for (var entry : players.entrySet()) {
            int x, y;
            switch (entry.getKey()) {
                case MYSELF:
                    x = carpetArea.x;
                    y = carpetArea.y + carpetArea.height + renderingArea.height / 20;
                    break;
                case ACROSS:
                    x = carpetArea.x;
                    y = carpetArea.y - renderingArea.height / 20;
                    break;
                case LEFT:
                    x = renderingArea.x + renderingArea.width / 6;
                    y = carpetArea.y - renderingArea.height / 10;
                    break;
                case RIGHT:
                    x = renderingArea.x + renderingArea.width - renderingArea.width / 6 - cardHeight;
                    y = carpetArea.y - renderingArea.height / 10;
                    break;
                default:
                    throw new IndexOutOfBoundsException("Unknown position " + entry.getKey());
            }
            g2.drawString(entry.getValue().getName(), x, y);
        }
    }


}
