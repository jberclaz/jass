/*
 * CanvasLastPlie.java
 *
 * Created on 19. avril 2000, 11:23
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version
 */

package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CanvasLastPlie extends Canvas {
  private List<Card> lastPlie = new ArrayList<>();
  private int atout;
  private int ourScore, theirScore;

  public CanvasLastPlie() {
    ourScore = 0;
    theirScore = 0;
    atout = 4;      // ne rien afficher
  }

  public void setLastPlie(Collection<Card> plie) {
    lastPlie.clear();
    lastPlie.addAll(plie);
  }

  public void setScore(int ourScore, int theirScore) {
    this.ourScore = ourScore;
    this.theirScore = theirScore;
  }

  public void setAtout(int atout) { this.atout = atout; }

  public void paint (Graphics g) {
    if (!lastPlie.isEmpty()) {
      for (int i = 0; i < 4; i++) {
        g.drawImage(CardImages.getInstance().getImage(lastPlie.get(i)),
                120 + 30 * i, 5, this);
      }
    }
    if (atout < 4)
      g.drawImage(CardImages.getInstance().getColorImage(atout), 380, 8, 15, 15, this);
    g.drawString("Dernière plie:", 20, 20);
    g.drawString("Atout:", 340, 20);
    g.drawString("Nous: " + String.valueOf(ourScore), 420, 13);
    g.drawString("Eux : " + String.valueOf(theirScore), 420, 27);
  }
}
