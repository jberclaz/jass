
//Titre :        Flat Jass Client System
//Version :
//Copyright :    Copyright (c) 1998
//Auteur :       Berclaz Jérome et Métrailler Pierre
//Société :      Flat
//Description :  Client du jeu de Jass


package com.leflat.jass.client;

import java.awt.*;
/*
public class CanvasBorder extends Canvas{
  private Image cardBack;
  Image card;
  int nbrCards;
  public String name;
  public int mode;    // 0 : tirage des équipes, 1 : jouer normalement

  public CanvasBorder(String imgPath) {
    mode = 1;
    nbrCards = 0;
    name = "";
    Toolkit tk = Toolkit.getDefaultToolkit();
    //cardBack = tk.getImage("z:/jberclaz/jass/flatjassclientproject/pictures/dos.gif");
    cardBack = tk.getImage(imgPath+"dos.gif");
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
  }
}
*/