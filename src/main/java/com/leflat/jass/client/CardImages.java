package com.leflat.jass.client;

import com.leflat.jass.common.Card;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CardImages {
    private final static Logger LOGGER = Logger.getLogger(CardImages.class.getName());
    public static final int IMG_WIDTH = 193;
    public static final int IMG_HEIGHT = 300;
    private static final String IMG_PATH = "pics/swiss/";
    private static final BufferedImage[] images = new BufferedImage[36];
    private static BufferedImage backImage;
    private static final BufferedImage[] colorImages = new BufferedImage[4];

    static {
        for (int i = 0; i < 36; i++) {
            var imagePath = CardImages.class.getClassLoader().getResource(imagePath(i));
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
        var backImagePath = CardImages.class.getClassLoader().getResource(IMG_PATH + "back.png");
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

    private static String imagePath(int number) {
        return IMG_PATH + String.format("%02d.png", number);
    }

    public static BufferedImage getImage(Card card) {
        if (!card.isBack()) {
            return images[card.getNumber()];
        }
        return backImage;
    }

    public static BufferedImage getBackImage() {
        return backImage;
    }

    public static BufferedImage getColorImage(int color) {
        return colorImages[color];
    }
}
