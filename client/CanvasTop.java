/*
 * CanvasTop.java
 *
 * Created on 18. avril 2000, 17:31
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version
 */
import java.awt.*;

public class CanvasTop extends JassCanvas{
    Image   cardBack;         // image de la carte
    int     nbrCards;           // nombre de cartes en main
    Image   card;
    // mode:: 0 : tirage des équipes, 1 : jouer normalement

    // Constructeur
    public CanvasTop(String imgPath) {
	name       = "";
	mode       = 1;
	nbrCards   = 0;
	Toolkit tk = Toolkit.getDefaultToolkit();
	cardBack   = tk.getImage(imgPath+"dos.gif");
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
	    int w = (d.width - 36 - nbrCards * 35) / 2;
	    int h = (d.height - 96) / 2 + 10;
	    for (int i = 0; i<nbrCards; i++)
		g.drawImage(cardBack, w + 35 * i, 20, 71, 96, this);
	}
	else {
	    int w = (d.width - 71) / 2 + 50;
	    int h = (d.height - 96) / 2 + 10;
	    g.drawImage(card, w, 20, 71, 96, this);
	}
	g.drawString(name, 30, 15);
	if (atout)
	    g.drawString("atout", name.length() * 7 + 60, 15);
    }
}
