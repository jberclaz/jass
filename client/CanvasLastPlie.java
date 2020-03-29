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
import java.awt.*;

public class CanvasLastPlie extends Canvas {
  public Image[] cards = new Image[4];
  private Image[] colors = new Image[4];
  public int atout;
  public boolean display;
  public int ourScore, theirScore;

  public CanvasLastPlie(String imgPath) {
    ourScore = 0;
    theirScore = 0;
    display = false;
    atout = 4;      // ne rien afficher
    Toolkit tk = Toolkit.getDefaultToolkit();
    //cardBack = tk.getImage("z:/jberclaz/jass/flatjassclientproject/pictures/dos.gif");
    for (int i=0; i<4; i++)
      colors[i] = tk.getImage(imgPath+"c" + String.valueOf(i) + ".gif");
  }

  public void paint (Graphics g) {
    if (display)
      for (int i=0; i<4; i++)
        g.drawImage(cards[i], 120 + 30 * i, 5, 71, 96, this);
    if (atout < 4)
      g.drawImage(colors[atout], 380, 8, 15, 15, this);
    g.drawString("Dernière plie:", 20, 20);
    g.drawString("Atout:", 340, 20);
    g.drawString("Nous: " + String.valueOf(ourScore), 420, 13);
    g.drawString("Eux : " + String.valueOf(theirScore), 420, 27);
  }
} 
