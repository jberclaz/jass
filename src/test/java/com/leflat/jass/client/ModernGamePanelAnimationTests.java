package com.leflat.jass.client;

import com.leflat.jass.common.Card;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ModernGamePanelAnimationTests {
    @Test
    void test_no_click_and_changes_delayed_during_animation() throws Exception {
        var panelRef = new AtomicReference<ModernGamePanel>();
        var updated = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ModernGamePanel();
            panelRef.set(panel);
            var positions = List.of(PlayerPosition.MYSELF, PlayerPosition.RIGHT, PlayerPosition.ACROSS, PlayerPosition.LEFT);
            for (int i = 0; i < 4; i++) {
                panel.setPlayer(positions.get(i), new ClientPlayer(i, "Player " + i));
                panel.setHand(positions.get(i), new ArrayList<>(Collections.nCopies(5, Card.getBack())));
                panel.setPlayedCard(positions.get(i), new Card(i * 9));
            }
            // a played card moves to the carpet
            assertEquals(ModernGamePanel.GameMode.ANIMATION, panel.getMode());
            panel.setSize(630, 530);
            panel.setMode(ModernGamePanel.GameMode.GAME);
            panel.setInteractive(true);

            panel.collectCards(PlayerPosition.LEFT);
            assertEquals(ModernGamePanel.GameMode.ANIMATION, panel.getMode());
            // neither on the carpet (where the cards are drawn to choose the teams) nor on the hand
            assertNull(panel.getCard(315, 225));
            assertNull(panel.getCard(315, 400));
            panel.runAfterAnimation(() -> updated.set(true));
            assertFalse(updated.get());
        });

        long deadline = System.currentTimeMillis() + 5000;
        var mode = new AtomicReference<ModernGamePanel.GameMode>();
        do {
            Thread.sleep(50);
            SwingUtilities.invokeAndWait(() -> mode.set(panelRef.get().getMode()));
        } while (mode.get() == ModernGamePanel.GameMode.ANIMATION && System.currentTimeMillis() < deadline);

        assertEquals(ModernGamePanel.GameMode.GAME, mode.get());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(updated.get());
            // once the animation is over, changes are applied right away
            updated.set(false);
            panelRef.get().runAfterAnimation(() -> updated.set(true));
            assertTrue(updated.get());
        });
    }
}
