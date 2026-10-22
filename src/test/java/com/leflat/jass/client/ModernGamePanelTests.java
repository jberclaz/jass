package com.leflat.jass.client;

import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.Card;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ModernGamePanelTests {
    @Test
    void test_paint_announcements() {
        var panel = new ModernGamePanel();
        var positions = List.of(PlayerPosition.MYSELF, PlayerPosition.RIGHT, PlayerPosition.ACROSS, PlayerPosition.LEFT);
        for (int i = 0; i < 4; i++) {
            panel.setPlayer(positions.get(i), new ClientPlayer(i, "Player " + i));
            panel.setHand(positions.get(i), new ArrayList<>(Collections.nCopies(8, Card.getBack())));
        }
        panel.setMode(ModernGamePanel.GameMode.GAME);
        for (var position : positions) {
            panel.addAnnouncements(position, List.of(new Announcement(Announcement.FIFTY, new Card(Card.RANK_ROI, Card.COLOR_CLUB)),
                    new Announcement(Announcement.SQUARE, new Card(Card.RANK_DAME, Card.COLOR_SPADE))));
        }
        panel.addAnnouncements(PlayerPosition.RIGHT, List.of(Announcement.getStoeck()));

        // small enough for the announcements of the side players to wrap
        panel.setSize(400, 340);
        var image = new BufferedImage(400, 340, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setClip(0, 0, 400, 340);
        assertDoesNotThrow(() -> panel.paint(g));

        panel.clearCards();
        assertDoesNotThrow(() -> panel.paint(g));
    }
}
