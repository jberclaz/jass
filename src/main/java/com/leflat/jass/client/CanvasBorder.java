/*
 * CanvasBorder.java
 *
 * Created on 18. avril 2000, 16:49
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version
 */

import java.awt.*;

public class CanvasBorder extends JassCanvas {
    Image   cardBack;
    Image   card;
    int     nbrCards;
    // mode:: 0 : tirage des équipes, 1 : jouer normalement


    /** Creates new CanvasBorder */
    public CanvasBorder(String imgPath) {
	mode      = 1;
	nbrCards  = 0;
	name      = "";
	Toolkit tk = Toolkit.getDefaultToolkit();

	cardBack = tk.getImage(imgPath+"dos.gif");
    }

    public void setNbrCards(int nbrCards) {
	this.nbrCards = nbrCards;
    }

    public void setCard(Image card) {
	this.card = card;
    }

    public void removeCard() {
	nbrCards--;
    }

    public void paint (Graphics g) {
	Dimension d = getSize();
	if (mode == 1) {
	    int w = (d.width - 71) / 2;
	    int h = (d.height - nbrCards * 30 - 66) / 2 + 20;
	    for (int i = 0; i<nbrCards; i++)
		g.drawImage(cardBack, w, h + i * 30, 71, 96, this);
	}
	else {
	    int w = (d.width - 71) / 2;
	    int h = (d.height - 96) / 2 + 20;
	    g.drawImage(card, w, h, 71, 96, this);
	}
	g.drawString(name, 20, 30);
	if (atout)
	    g.drawString("atout", 20, 60);
    }
}
