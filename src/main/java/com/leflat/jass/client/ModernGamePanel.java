package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

import static java.lang.Math.*;

public class ModernGamePanel extends JPanel {
    enum GameMode {
        TEAM_DRAWING, GAME
    }

    private Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private static final float ASPECT_RATIO = 4.0f / 3;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private GameMode gameMode;
    private Set<Integer> drawnCards = new HashSet<>();
    private Map<PlayerPosition, Card> playedCards = new HashMap<>();
    private Map<PlayerPosition, Float> cardAngles = new HashMap<>();
    private final Random random = new Random();

    public ModernGamePanel() {
        super();
        gameMode = GameMode.TEAM_DRAWING;
        drawnCards.clear();
        setDoubleBuffered(true);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Dimension d = getSize();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        var renderingArea = getRenderingDimension();

        int card_height = renderingArea.height / 7;
        int card_width = round((float) card_height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);

        // draw carpet
        g2.setColor(CARPET_COLOR);
        int carpet_width = renderingArea.width / 3;
        int carpet_height = renderingArea.height / 3;
        int carpet_x_offset = renderingArea.width / 3 + renderingArea.x;
        int carpet_y_offset = renderingArea.height / 3 + renderingArea.y;
        g2.fillRoundRect(carpet_x_offset, carpet_y_offset, carpet_width, carpet_height, renderingArea.width / 40, renderingArea.width / 40);

        if (gameMode == GameMode.TEAM_DRAWING) {
            for (int i = 0; i < 36; i++) {
                int card_x_offset = carpet_width / 10;
                float card_x_step = (carpet_width - 2 * card_x_offset - card_width) / 35.0f;
                int card_y_offset = (carpet_height - card_height) / 2 + carpet_y_offset;
                if (!drawnCards.contains(i)) {
                    g2.drawImage(CardImages.getInstance().getBackImage(), round(carpet_x_offset + card_x_offset + i * card_x_step),
                            card_y_offset, card_width, card_height, this);
                }
            }
        } else {
            for (var entry : playedCards.entrySet()) {
                switch (entry.getKey()) {
                    case MYSELF:
                        g2.drawImage(CardImages.getInstance().getImage(entry.getValue()),
                                carpet_x_offset + (carpet_width - card_width) / 2,
                                carpet_y_offset + (carpet_height - card_height) - card_height / 10,
                                card_width, card_height, this);
                        break;
                    case ACROSS:
                        g2.drawImage(CardImages.getInstance().getImage(entry.getValue()),
                                carpet_x_offset + (carpet_width - card_width) / 2,
                                carpet_y_offset + card_height / 10,
                                card_width, card_height, this);
                        break;
                    case LEFT:
                        g2.drawImage(CardImages.getInstance().getImage(entry.getValue()),
                                carpet_x_offset + card_width,
                                carpet_y_offset + (carpet_height - card_height) / 2,
                                card_width, card_height, this);
                        break;
                    case RIGHT:
                        g2.drawImage(CardImages.getInstance().getImage(entry.getValue()),
                                carpet_x_offset + carpet_width - card_width * 2,
                                carpet_y_offset + (carpet_height - card_height) / 2,
                                card_width, card_height, this);
                        break;
                }
            }
        }
        for (var entry : players.entrySet()) {
            switch (entry.getKey()) {
                case MYSELF:
                    var hand = entry.getValue().getHand();
                    int hand_width = carpet_width;
                    int hand_x_offset = carpet_x_offset;
                    int hand_y_offset = renderingArea.y + renderingArea.height - card_height - renderingArea.height / 10;
                    float card_x_step = (hand_width - card_width) / (float) (hand.size() - 1);
                    for (int i = 0; i < hand.size(); i++) {
                        g2.drawImage(CardImages.getInstance().getImage(hand.get(i)),
                                hand_x_offset + round(i * card_x_step), hand_y_offset,
                                card_width, card_height, this);
                    }
                    break;
                case ACROSS:
                    hand = entry.getValue().getHand();
                    hand_width = carpet_width;
                    hand_x_offset = carpet_x_offset;
                    hand_y_offset = renderingArea.y + renderingArea.height / 10;
                    card_x_step = (hand_width - card_width) / (float) (hand.size() - 1);
                    for (int i = hand.size() - 1; i >= 0; i--) {
                        g2.drawImage(CardImages.getInstance().getImage(hand.get(i)),
                                hand_x_offset + round(i * card_x_step), hand_y_offset,
                                card_width, card_height, this);
                    }
                    break;
                case LEFT:
                    hand = entry.getValue().getHand();
                    float scale = (float) card_height / CardImages.IMG_HEIGHT;
                    int hand_height = carpet_height;
                    float card_y_step = (hand_height - card_width) / (float) (hand.size() - 1);
                    hand_x_offset = renderingArea.x + renderingArea.width / 6;
                    hand_y_offset = carpet_y_offset;
                    for (int i = hand.size() - 1; i >= 0; i--) {
                        var xform = new AffineTransform();
                        xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getInstance().getImage(hand.get(i)), xform);
                    }
                    break;
                case RIGHT:
                    hand = entry.getValue().getHand();
                    scale = (float) card_height / CardImages.IMG_HEIGHT;
                    hand_height = carpet_height;
                    card_y_step = (hand_height - card_width) / (float) (hand.size() - 1);
                    hand_x_offset = renderingArea.x + renderingArea.width - renderingArea.width / 6 - card_height;
                    hand_y_offset = carpet_y_offset;
                    for (int i = 0; i < hand.size(); i++) {
                        var xform = new AffineTransform();
                        xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getInstance().getImage(hand.get(i)), xform);
                    }
                    break;
            }
        }

        // draw names
        for (var entry : players.entrySet()) {
            int x, y;
            switch (entry.getKey()) {
                case MYSELF:
                    x = carpet_x_offset;
                    y = carpet_y_offset + carpet_height + renderingArea.height / 20;
                    break;
                case ACROSS:
                    x = carpet_x_offset;
                    y = carpet_y_offset - renderingArea.height / 20;
                    break;
                case LEFT:
                    x = renderingArea.x + renderingArea.width / 6;
                    y = carpet_y_offset - renderingArea.height / 10;
                    break;
                case RIGHT:
                    x = renderingArea.x + renderingArea.width - renderingArea.width / 6 - card_height;
                    y = carpet_y_offset - renderingArea.height / 10;
                    break;
                default:
                    throw new IndexOutOfBoundsException("Unknown position " + entry.getKey());
            }
            g2.drawString(entry.getValue().getName(), x, y);
        }
    }


}
