package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static java.lang.Math.round;

public class ModernGamePanel extends JPanel {
    enum GameMode {
        TEAM_DRAWING, GAME
    }
    private Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private static final float ASPECT_RATIO = 4.0f / 3;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private GameMode gameMode;
    private Set<Integer> drawnCards = new HashSet<>();

    public ModernGamePanel() {
        super();
        gameMode = GameMode.TEAM_DRAWING;
        drawnCards.clear();
        setDoubleBuffered(true);
    }

    public void setPlayer(PlayerPosition position, BasePlayer player) {
        players.put(position, player);
    }

    public void setHand(PlayerPosition position, List<Card> hand) {
        try {
            players.get(position).setHand(hand);
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Dimension d = getSize();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int rendering_width, rendering_height;
        int x_offset, y_offset;
        if ((float) d.width / d.height > ASPECT_RATIO) {
            rendering_width = round(d.height * ASPECT_RATIO);
            rendering_height = d.height;
            x_offset = (d.width  - rendering_width) / 2;
            y_offset = 0;
        } else {
            rendering_width = d.width;
            rendering_height = round(d.width / ASPECT_RATIO);
            x_offset = 0;
            y_offset = (d.height - rendering_height) / 2;
        }

        int card_height = rendering_height / 7;
        int card_width = (int)round(card_height / 1.5);

        // draw carpet
        g2.setColor(CARPET_COLOR);
        int carpet_width = rendering_width / 3;
        int carpet_height = rendering_height / 3;
        int carpet_x_offset = rendering_width / 3 + x_offset;
        int carpet_y_offset = rendering_height / 3 + y_offset;
        g2.fillRoundRect(carpet_x_offset, carpet_y_offset, carpet_width, carpet_height, rendering_width / 30, rendering_width / 30);

        if (gameMode == GameMode.TEAM_DRAWING) {

            for (int i=0; i<36; i++) {
                int card_x_offset = carpet_width / 10;
                float card_x_step = (carpet_width - 2 * card_x_offset - card_width) / 35.0f;
                int card_y_offset = (carpet_height - card_height) / 2 + carpet_y_offset;
                if (!drawnCards.contains(i)) {
                    g2.drawImage(CardImages.getInstance().getBackImage(), round(carpet_x_offset + card_x_offset + i * card_x_step),
                            card_y_offset,  card_width, card_height, this);
                }
            }
        }
    }


}
