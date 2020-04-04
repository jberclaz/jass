package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import java.awt.*;

public class CardImages {
    public static final int IMG_WIDTH = 71;
    public static final int IMG_HEIGHT = 96;
    private static final String IMG_PATH = "pics/";
    private static final CardImages singleton = new CardImages();
    private Image[] images = new Image[36];
    private Image backImage;
    private Image[] colorImages = new Image[4];

    private CardImages() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        for (int i = 0; i < 36; i++) {
            images[i] = tk.getImage(IMG_PATH + i + ".png");
        }
        backImage = tk.getImage(IMG_PATH + "dos.png");
        for (int i = 0; i < 4; i++) {
            colorImages[i] = tk.getImage(IMG_PATH + "c" + i + ".png");
        }
    }

    public static CardImages getInstance() {
        return singleton;
    }

    public Image getImage(Card card) {
        if (!card.isBack()) {
            return images[card.getNumber()];
        }
        return backImage;
    }

    public Image getBackImage() { return backImage; }

    public Image getColorImage(int color) { return colorImages[color]; }
}
