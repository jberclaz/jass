/*
 * CanvasCenter.java
 *
 * Created on 18. avril 2000, 18:04
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version 1.1
 */
import java.awt.*;

public class CanvasCenter extends Canvas{
  private Image cardBack;
  public int mode;        // 0 : rien, 1 : tirer les équipes
                          // 2 : tirer les équipes et choisir une carte
                          // 3 : jouer
  public int[] cardsChoosen = new int[36];
  private Image[] cards = new Image[36];


  public CanvasCenter(String imgPath) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    cardBack = tk.getImage(imgPath+"dos.gif");
    for (int i = 0; i<36; i++)
      cards[i] = tk.getImage(imgPath+ String.valueOf(i) + ".gif");
      //cards[i] = tk.getImage("z:/jberclaz/jass/flatjassclientproject/pictures/" + String.valueOf(i) + ".gif");
    mode = 0;
  }

  public void paint (Graphics g) {
    // System.out.println("Repaint");
    Dimension d = getSize();
    switch (mode) {
      case 1 :
      case 2 :  for (int i=0; i<36; i++)
                  if (cardsChoosen[i] == 10)
                    g.drawImage(cardBack, 10 + i * 8, 40, 71, 96, this);
                break;
      case 3 :  int w = d.width / 2;
                int h = d.height / 2;
                if (cardsChoosen[0] < 36)       // joueur
                  g.drawImage(cards[cardsChoosen[0]], w - 35, h + 2, 71, 96, this);
                if (cardsChoosen[1] < 36)       // gauche
                  g.drawImage(cards[cardsChoosen[1]], w + 25, h - 48, 71, 96, this);
                if (cardsChoosen[2] < 36)       // haut
                  g.drawImage(cards[cardsChoosen[2]], w - 35, h - 98, 71, 96, this);
                if (cardsChoosen[3] < 36)       // droite
                  g.drawImage(cards[cardsChoosen[3]], w - 95, h - 48, 71, 96, this);
    }
  }
}
