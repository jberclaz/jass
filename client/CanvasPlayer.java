/*
 * CanvasPlayer.java
 *
 * Created on 18. avril 2000, 17:31
 */
 


/** 
 *
 * @author  Berclaz Jérôme
 * @version 
 */
import java.awt.*;

public class CanvasPlayer extends JassCanvas{
    Image[] cards;
    int[] hand;
    //  mode::  0 : rien, 1 : jouer en premier, 2 : jouer

    public CanvasPlayer(String imgPath) {
	name   = "";
	mode   = 0;
	hand   = new int[9];
	cards  = new Image[36];
	for (int i=0; i<9; i++)
	    hand[i] = 37;          // aucune carte
	Toolkit tk = Toolkit.getDefaultToolkit();
	for (int i = 0; i<36; i++)
	    cards[i] = tk.getImage(imgPath + String.valueOf(i) + ".gif");
    }

    public void paint (Graphics g) {
	Dimension d = getSize();
	int w = (d.width - 9 * 35 - 36) / 2;
	int h = (d.height - 96) / 2 + 10;
	for (int i = 0; i<9; i++)
	    if (hand[i] < 36)
		g.drawImage(cards[hand[i]], 20+i*35, 20, 71, 96, this);
	g.drawString(name, 30, 15);
	if (atout)
	    g.drawString("atout", name.length() * 7 + 60, 15);
    }
}
