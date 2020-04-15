package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CardImages {
    private final static Logger LOGGER = Logger.getLogger(CardImages.class.getName());
    public static final int IMG_WIDTH = 71;
    public static final int IMG_HEIGHT = 96;
    private static final String IMG_PATH = "pics/";
    private static final Image[] images = new Image[36];
    private static Image backImage;
    private static final Image[] colorImages = new Image[4];

    static {
        for (int i = 0; i < 36; i++) {
            var imagePath = CardImages.class.getClassLoader().getResource(IMG_PATH + i + ".png");
            if (imagePath == null) {
                LOGGER.severe("Unable to locate card image " + i);
                continue;
            }
            try {
                images[i] = ImageIO.read(imagePath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to open image " + imagePath, e);
            }
        }
        var backImagePath = CardImages.class.getClassLoader().getResource(IMG_PATH + "dos.png");
        if (backImagePath != null) {
            try {
                backImage = ImageIO.read(backImagePath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Unable to open image " + backImage, e);
            }
        }
        else {
            LOGGER.severe("Unable to locate back card image");
        }
        for (int i = 0; i < 4; i++) {
            var colorImagePath = CardImages.class.getClassLoader().getResource(IMG_PATH + "c" + i + ".png");
            if (colorImagePath == null) {
                LOGGER.severe("Unable to locate color image " + i);
                continue;
            }
            try {
                colorImages[i] = ImageIO.read(colorImagePath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to open image " + colorImagePath, e);
            }
        }
    }

    public static Image getImage(Card card) {
        if (!card.isBack()) {
            return images[card.getNumber()];
        }
        return backImage;
    }

    public static Image getBackImage() {
        return backImage;
    }

    public static Image getColorImage(int color) {
        return colorImages[color];
    }
}
