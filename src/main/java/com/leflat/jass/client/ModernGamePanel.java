package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

import static java.lang.Math.round;
import static java.lang.Math.toRadians;

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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Dimension d = getSize();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int rendering_width, rendering_height;
        int x_offset, y_offset;
        if ((float) d.width / d.height > ASPECT_RATIO) {
            rendering_width = round(d.height * ASPECT_RATIO);
            rendering_height = d.height;
            x_offset = (d.width - rendering_width) / 2;
            y_offset = 0;
        } else {
            rendering_width = d.width;
            rendering_height = round(d.width / ASPECT_RATIO);
            x_offset = 0;
            y_offset = (d.height - rendering_height) / 2;
        }

        int card_height = rendering_height / 7;
        int card_width = round((float) card_height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);

        // draw carpet
        g2.setColor(CARPET_COLOR);
        int carpet_width = rendering_width / 3;
        int carpet_height = rendering_height / 3;
        int carpet_x_offset = rendering_width / 3 + x_offset;
        int carpet_y_offset = rendering_height / 3 + y_offset;
        g2.fillRoundRect(carpet_x_offset, carpet_y_offset, carpet_width, carpet_height, rendering_width / 40, rendering_width / 40);

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
                    int hand_y_offset = y_offset + rendering_height - card_height - rendering_height / 10;
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
                    hand_y_offset = y_offset + rendering_height / 10;
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
                    hand_x_offset = x_offset + rendering_width / 6;
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
                    hand_x_offset = x_offset + rendering_width - rendering_width / 6 - card_height;
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
    }


}
