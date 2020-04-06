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
            var imagePath = getClass().getClassLoader().getResource(IMG_PATH + i + ".png");
            images[i] = tk.getImage(imagePath);
        }
        var backImagePath = getClass().getClassLoader().getResource(IMG_PATH + "dos.png");
        backImage = tk.getImage(backImagePath);
        for (int i = 0; i < 4; i++) {
            var colorImagePath = getClass().getClassLoader().getResource(IMG_PATH + "c" + i + ".png");
            colorImages[i] = tk.getImage(colorImagePath);
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

    public Image getBackImage() {
        return backImage;
    }

    public Image getColorImage(int color) {
        return colorImages[color];
    }
}
