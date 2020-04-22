package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.*;

public class ModernGamePanel extends JPanel {
    enum GameMode {
        TEAM_DRAWING, GAME, IDLE
    }

    private final static Logger LOGGER = Logger.getLogger(OriginalUi.class.getName());
    private final Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private static final float ASPECT_RATIO = 630f / 530;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private GameMode gameMode = GameMode.IDLE;
    private final Set<Integer> drawnCards = new HashSet<>();
    private final List<Card> lastPlie = new ArrayList<>();
    private final Map<PlayerPosition, Card> playedCards = new HashMap<>();
    private final Map<PlayerPosition, Float> cardAngles = new HashMap<>();
    private final Random random = new Random();
    private int ourScore;
    private int theirScore;
    private int atoutColor = -1;
    private boolean isInteractive = false;
    private JButton buttonAnnounce = new JButton("Annoncer");

    public ModernGamePanel() {
        super();
        drawnCards.clear();
        setDoubleBuffered(true);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            LOGGER.log(Level.WARNING, "Unable to set look and feel", e);
        }

        setLayout(null);
        add(buttonAnnounce);
        buttonAnnounce.setEnabled(false);

        addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                resizePanel(componentEvent);
            }

            @Override
            public void componentMoved(ComponentEvent componentEvent) {

            }

            @Override
            public void componentShown(ComponentEvent componentEvent) {

            }

            @Override
            public void componentHidden(ComponentEvent componentEvent) {

            }
        });
    }

    public void clearCards() {
        for (var p : players.values()) {
            try {
                p.setHand(Collections.emptyList());
            } catch (PlayerLeftExpection playerLeftExpection) {
                playerLeftExpection.printStackTrace();
            }
        }
        drawnCards.clear();
        lastPlie.clear();
        var renderingArea = getRenderingDimension();
        repaint(getInfoArea(renderingArea)
                .union(getCenterArea(renderingArea))
                .union(getInfoArea(renderingArea)));
    }

    public void setPlayer(PlayerPosition position, BasePlayer player) {
        players.put(position, player);
        repaintPlayerArea(position);
    }

    public void clearPlayer(PlayerPosition position) {
        players.remove(position);
    }

    public void setHand(PlayerPosition position, List<Card> hand) {
        try {
            players.get(position).setHand(hand);
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
        repaintPlayerArea(position);
    }

    public void setPlayedCard(PlayerPosition position, Card card) {
        playedCards.put(position, card);
        cardAngles.put(position, random.nextFloat() * 10 - 5f);
        repaint(getCenterArea(getRenderingDimension()));
    }

    public void collectPlie() {
        this.lastPlie.clear();
        this.lastPlie.addAll(playedCards.values());
        playedCards.clear();
        var renderingArea = getRenderingDimension();
        repaint(getInfoArea(renderingArea).union(getCenterArea(renderingArea)));
    }

    public void setMode(GameMode mode) {
        this.gameMode = mode;
        repaint(getCenterArea(getRenderingDimension()));
    }

    public GameMode getMode() {
        return gameMode;
    }

    public void setAtoutColor(int atoutColor) {
        this.atoutColor = atoutColor;
        var renderingArea = getRenderingDimension();
        repaint(getInfoArea(renderingArea));
    }

    public void hideAtout() {
        this.atoutColor = -1;
        var renderingArea = getRenderingDimension();
        repaint(getInfoArea(renderingArea));
    }

    public void setScores(int ourScore, int theirScore) {
        this.ourScore = ourScore;
        this.theirScore = theirScore;
        var renderingArea = getRenderingDimension();
        repaint(getInfoArea(renderingArea));
    }

    public void drawCard(int cardPosition, Card card, PlayerPosition playerPosition) {
        drawnCards.add(cardPosition);
        try {
            players.get(playerPosition).setHand(Collections.singletonList(card));
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
        repaint(getCenterArea(getRenderingDimension()));
        repaintPlayerArea(playerPosition);
    }

    public void removeCard(PlayerPosition playerPosition) {
        removeCard(playerPosition, 0);
        repaintPlayerArea(playerPosition);
    }

    public void removeCard(PlayerPosition playerPosition, int cardPosition) {
        players.get(playerPosition).getHand().remove(cardPosition);
        repaintPlayerArea(playerPosition);
    }

    public void setInteractive(boolean interactive) {
        this.isInteractive = interactive;
    }

    public boolean isInsidePlayerCardsArea(int x, int y) {
        return getHandArea(getRenderingDimension()).contains(x, y);
    }

    public Card getCard(int x, int y) {
        if (!isInteractive) {
            return null;
        }
        var renderingArea = getRenderingDimension();
        if (gameMode == GameMode.GAME) {
            var handArea = getHandArea(renderingArea);
            if (!handArea.contains(x, y)) {
                return null;
            }
            var hand = players.get(PlayerPosition.MYSELF).getHand();
            var card_width = round((float) handArea.height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);
            float card_x_step = (handArea.width - card_width) / (float) (hand.size() - 1);
            int cardNumber = (int) floor((x - handArea.x) / card_x_step);
            return hand.get(min(cardNumber, hand.size() - 1));
        } else {
            var cardArea = getTeamDrawingArea(renderingArea);
            if (!cardArea.contains(x, y)) {
                return null;
            }
            var cardWidth = round((float) cardArea.height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);
            float xStep = (cardArea.width - cardWidth) / 35f;
            int highNumber = (int) floor((x - cardArea.x) / xStep);
            int lowNumber = (int) floor((x - cardArea.x - cardWidth) / xStep);
            for (int number = min(highNumber, 35); number >= max(0, lowNumber); number--) {
                if (!drawnCards.contains(number)) {
                    return new Card(number);
                }
            }
        }
        return null;
    }

    private void repaintPlayerArea(PlayerPosition position) {
        var area = getRenderingDimension();
        switch (position) {
            case MYSELF:
                repaint(getPlayerArea(area));
                break;
            case ACROSS:
                repaint(getAcrossArea(area));
                break;
            case LEFT:
                repaint(getLeftArea(area));
                break;
            case RIGHT:
                repaint(getRightArea(area));
                break;
        }
    }

    private Dimension getCardDimension(Rectangle renderingArea) {
        float scale = renderingArea.height / 530f;
        float height = 96f * scale;
        int width = round(height / CardImages.IMG_HEIGHT * CardImages.IMG_WIDTH);
        return new Dimension(width, round(height));
    }

    private Rectangle getHandArea(Rectangle renderingArea) {
        var player = players.get(PlayerPosition.MYSELF);
        if (player == null ) {
            return new Rectangle(0, 0, 0, 0);
        }
        var hand = player.getHand();
        if (hand.size() == 0) {
            return new Rectangle(0, 0, 0, 0);
        }
        var playerArea = getPlayerArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea);
        int hand_width = round((hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width);
        int hand_x_offset = playerArea.x + (playerArea.width - hand_width) / 2;
        int hand_y_offset = playerArea.y + round(playerArea.height * 20f / 120f);
        return new Rectangle(hand_x_offset, hand_y_offset, hand_width, cardDimension.height);
    }

    private Rectangle getCardArea(int number, Rectangle area) {
        var handArea = getHandArea(area);
        Rectangle card = new Rectangle();
        card.width = round((float) handArea.height * (float) CardImages.IMG_WIDTH / CardImages.IMG_HEIGHT);

        float card_x_step = (handArea.width - card.width) / (float) (players.get(PlayerPosition.MYSELF).getHand().size() - 1);
        card.x = handArea.x + round(number * card_x_step);
        card.y = handArea.y;
        return card;
    }

    private Rectangle getTeamDrawingArea(Rectangle renderingArea) {
        var centerArea = getCenterArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea);
        return new Rectangle(centerArea.x + round(centerArea.width / 10f),
                centerArea.y + (centerArea.height - cardDimension.height) / 2,
                round(centerArea.width * .8f),
                cardDimension.height);
    }

    private Rectangle getRenderingDimension() {
        Dimension d = getSize();
        var area = new Rectangle();
        if ((float) d.width / d.height > ASPECT_RATIO) {
            area.width = round(d.height * ASPECT_RATIO);
            area.height = d.height;
            area.x = (d.width - area.width) / 2;
            area.y = 0;
        } else {
            area.width = d.width;
            area.height = round(d.width / ASPECT_RATIO);
            area.x = 0;
            area.y = (d.height - area.height) / 2;
        }
        return area;
    }

    Rectangle getCenterArea(Rectangle renderingArea) {
        int carpet_width = round(renderingArea.width * 390f / 630f);
        int carpet_height = round(renderingArea.height * 210f / 530f);
        int carpet_x_offset = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int carpet_y_offset = renderingArea.y + round(renderingArea.height * 120f / 530f);
        return new Rectangle(carpet_x_offset, carpet_y_offset, carpet_width, carpet_height);
    }

    Rectangle getPlayerArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 390f / 630f);
        int height = round(renderingArea.height * 120f / 530f);
        int x = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int y = renderingArea.y + round(renderingArea.height * 330f / 530f);
        return new Rectangle(x, y, width, height);
    }

    Rectangle getAcrossArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 390f / 630f);
        int height = round(renderingArea.height * 120f / 530f);
        int x = renderingArea.x + round(renderingArea.width * 120f / 630f);
        int y = renderingArea.y;
        return new Rectangle(x, y, width, height);
    }

    Rectangle getLeftArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 120f / 630f);
        int height = round(renderingArea.height * 450f / 530f);
        int x = renderingArea.x;
        int y = renderingArea.y;
        return new Rectangle(x, y, width, height);
    }

    Rectangle getRightArea(Rectangle renderingArea) {
        int width = round(renderingArea.width * 120f / 630f);
        int height = round(renderingArea.height * 450f / 530f);
        int x = renderingArea.x + round(renderingArea.width * 510f / 630f);
        int y = renderingArea.y;
        return new Rectangle(x, y, width, height);
    }

    Rectangle getInfoArea(Rectangle renderingArea) {
        int width = renderingArea.width;
        int height = round(renderingArea.height * 40f / 530f);
        int x = renderingArea.x;
        int y = renderingArea.y + round(renderingArea.height * 450f / 530f);
        ;
        return new Rectangle(x, y, width, height);
    }

    void paintInfo(Graphics2D g2, Rectangle infoArea, Dimension cardDimension) {
        float x_step = 30f * infoArea.width / 630f;
        float x_offset = infoArea.x + 120f * infoArea.width / 630f;
        int y_offset = infoArea.y + round(5f * infoArea.height / 40f);
        for (int i = 0; i < lastPlie.size(); i++) {
            int card_x = round(x_offset + x_step * i);
            g2.drawImage(CardImages.getImage(lastPlie.get(i)),
                    card_x, y_offset,
                    card_x + cardDimension.width, y_offset + round(35f / 40f * infoArea.height),
                    0, 0,
                    CardImages.IMG_WIDTH, round(35f / 96f * CardImages.IMG_HEIGHT),
                    this);
        }
        int last_plie_x = round(20f / 40 * infoArea.height);
        g2.drawString("Dernière plie:", infoArea.x + last_plie_x,
                infoArea.y + last_plie_x);
        if (atoutColor >= 0) {
            int atout_x = round(340f / 40f * infoArea.height);
            g2.drawString("Atout:", infoArea.x + atout_x,
                    infoArea.y + last_plie_x);
            int stringWidth = g2.getFontMetrics().stringWidth("Atout:  ");
            int colorSize = round(15f / 40f * infoArea.height);
            g2.drawImage(CardImages.getColorImage(atoutColor),
                    infoArea.x + atout_x + stringWidth,
                    infoArea.y + round(8f / 40f * infoArea.height),
                    colorSize, colorSize, this);
        }
        int score_label_x = round(420f / 40f * infoArea.height);
        int our_score_y = round(13f / 40f * infoArea.height);
        g2.drawString("Nous:", infoArea.x + score_label_x,
                infoArea.y + our_score_y);
        int their_score_y = round(27f / 40f * infoArea.height);
        g2.drawString("Eux:", infoArea.x + score_label_x,
                infoArea.y + their_score_y);
        int score_x = round(470f / 40f * infoArea.height);
        g2.drawString(String.valueOf(ourScore), infoArea.x + score_x,
                infoArea.y + our_score_y);
        g2.drawString(String.valueOf(theirScore), infoArea.x + score_x,
                infoArea.y + their_score_y);
    }

    void paintCenter(Graphics2D g2, Rectangle centerArea, Dimension cardDimension) {
        // draw carpet
        var color = g2.getColor();
        g2.setColor(CARPET_COLOR);

        g2.fillRoundRect(centerArea.x, centerArea.y, centerArea.width, centerArea.height, centerArea.width / 20, centerArea.width / 20);
        g2.setColor(color);
        float scale = (float) cardDimension.height / CardImages.IMG_HEIGHT;

        if (gameMode == GameMode.TEAM_DRAWING) {
            for (int i = 0; i < 36; i++) {
                float card_x_offset = centerArea.width / 10f;
                float card_x_step = (centerArea.width - 2 * card_x_offset - cardDimension.width) / 35.0f;
                int card_y_offset = (centerArea.height - cardDimension.height) / 2 + centerArea.y;
                if (!drawnCards.contains(i)) {
                    g2.drawImage(CardImages.getBackImage(), round(centerArea.x + card_x_offset + i * card_x_step),
                            card_y_offset, cardDimension.width, cardDimension.height, this);
                }
            }
        } else {
            for (var entry : playedCards.entrySet()) {
                switch (entry.getKey()) {
                    case MYSELF:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                centerArea.x + (centerArea.width - cardDimension.width) / 2,
                                centerArea.y + (centerArea.height - cardDimension.height) - round(cardDimension.height / 12f),
                                cardDimension.width, cardDimension.height, this);
                        break;
                    case ACROSS:
                        g2.drawImage(CardImages.getImage(entry.getValue()),
                                centerArea.x + (centerArea.width - cardDimension.width) / 2,
                                centerArea.y + round(cardDimension.height / 12f),
                                cardDimension.width, cardDimension.height, this);
                        break;
                    case LEFT:
                        var xform = new AffineTransform();
                        xform.translate(centerArea.x + cardDimension.width,
                                centerArea.y + (centerArea.height - cardDimension.width) / 2f);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90 + cardAngles.get(PlayerPosition.LEFT)));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getImage(entry.getValue()), xform);
                        break;
                    case RIGHT:
                        xform = new AffineTransform();
                        xform.translate(centerArea.x + centerArea.width - cardDimension.width - cardDimension.height,
                                centerArea.y + (centerArea.height - cardDimension.width) / 2f);
                        xform.scale(scale, scale);
                        xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(-90 + cardAngles.get(PlayerPosition.RIGHT)));
                        xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
                        g2.drawRenderedImage(CardImages.getImage(entry.getValue()), xform);
                        break;
                }
            }
        }
    }

    void paintPlayerArea(Graphics2D g2, Rectangle playerArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.MYSELF);
        var hand = player.getHand();

        // cards
        int hand_width = round((hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width);
        int hand_x_offset = playerArea.x + (playerArea.width - hand_width) / 2;
        int hand_y_offset = playerArea.y + round(playerArea.height * 20f / 120f);
        float card_x_step = (hand_width - cardDimension.width) / (float) (hand.size() - 1);
        for (int i = 0; i < hand.size(); i++) {
            g2.drawImage(CardImages.getImage(hand.get(i)),
                    hand_x_offset + round(i * card_x_step), hand_y_offset,
                    cardDimension.width, cardDimension.height, this);
        }

        // name
        int nameX = playerArea.x + round(playerArea.width * 30f / 390f);
        int nameY = playerArea.y + round(playerArea.height * 15f / 120f);
        g2.drawString(player.getName(), nameX, nameY);
    }

    void paintAcrossArea(Graphics2D g2, Rectangle topArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.ACROSS);
        var hand = player.getHand();
        var hand_width = round((hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width);
        var hand_x_offset = topArea.x + (topArea.width - hand_width) / 2;
        var hand_y_offset = topArea.y + round(topArea.height * 20f / 120f);
        var card_x_step = (hand_width - cardDimension.width) / (float) (hand.size() - 1);
        for (int i = hand.size() - 1; i >= 0; i--) {
            g2.drawImage(CardImages.getImage(hand.get(i)),
                    hand_x_offset + round(i * card_x_step), hand_y_offset,
                    cardDimension.width, cardDimension.height, this);
        }

        // name
        int nameX = topArea.x + round(topArea.width * 30f / 390f);
        int nameY = topArea.y + round(topArea.height * 15f / 120f);
        g2.drawString(player.getName(), nameX, nameY);
    }

    void paintLeftArea(Graphics2D g2, Rectangle leftArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.LEFT);
        var hand = player.getHand();

        float scale = (float) cardDimension.height / CardImages.IMG_HEIGHT;
        int hand_height = round((hand.size()-1) * cardDimension.width / 2.1f + cardDimension.height);
        float card_y_step = hand.size() < 2 ? 0 : (hand_height - cardDimension.height) / (float) (hand.size() - 1);
        var hand_x_offset = leftArea.x + (leftArea.width - cardDimension.height) / 2;
        var hand_y_offset = leftArea.y + (leftArea.height - hand_height) / 2;
        for (int i = hand.size() - 1; i >= 0; i--) {
            var xform = new AffineTransform();
            xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
            xform.scale(scale, scale);
            xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
            xform.rotate(toRadians(90));
            xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(CardImages.getImage(hand.get(i)), xform);
        }

        // name
        int nameX = leftArea.x + round(leftArea.width * 20f / 120f);
        int nameY = leftArea.y + min(round(leftArea.height * 90f / 450f), hand_y_offset - cardDimension.width / 3);
        g2.drawString(player.getName(), nameX, nameY);
    }

    void paintRightArea(Graphics2D g2, Rectangle rightArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.RIGHT);
        var hand = player.getHand();

        var scale = (float) cardDimension.height / CardImages.IMG_HEIGHT;
        var hand_height = round((hand.size()-1) * cardDimension.width / 2.1f + cardDimension.height);
        var card_y_step = hand.size() < 2 ? 0 :(hand_height - cardDimension.width) / (float) (hand.size() - 1);
        var hand_x_offset = rightArea.x + (rightArea.width - cardDimension.height) / 2;
        var hand_y_offset = rightArea.y + (rightArea.height - hand_height) / 2;
        for (int i = 0; i < hand.size(); i++) {
            var xform = new AffineTransform();
            xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
            xform.scale(scale, scale);
            xform.translate(CardImages.IMG_HEIGHT / 2f, CardImages.IMG_WIDTH / 2f);
            xform.rotate(toRadians(-90));
            xform.translate(-CardImages.IMG_WIDTH / 2f, -CardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(CardImages.getImage(hand.get(i)), xform);
        }

        // name
        int nameX = rightArea.x + round(rightArea.width * 20f / 120f);
        int nameY = rightArea.y + min(round(rightArea.height * 90f / 450f), hand_y_offset - cardDimension.width / 3);
        g2.drawString(player.getName(), nameX, nameY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        var renderingArea = getRenderingDimension();

        var cardDimension = getCardDimension(renderingArea);

        var centerArea = getCenterArea(renderingArea);
        if (g2.getClip().intersects(centerArea)) {
            paintCenter(g2, centerArea, cardDimension);
        }

        // draw cards
        for (var entry : players.entrySet()) {
            switch (entry.getKey()) {
                case MYSELF:
                    var area = getPlayerArea(renderingArea);
                    if (g2.getClip().intersects(area)) {
                        paintPlayerArea(g2, area, cardDimension);
                    }
                    break;
                case ACROSS:
                    area = getAcrossArea(renderingArea);
                    if (g2.getClip().intersects(area)) {
                        paintAcrossArea(g2, area, cardDimension);
                    }
                    break;
                case LEFT:
                    area = getLeftArea(renderingArea);
                    if (g2.getClip().intersects(area)) {
                        paintLeftArea(g2, area, cardDimension);
                    }
                    break;
                case RIGHT:
                    area = getRightArea(renderingArea);
                    if (g2.getClip().intersects(area)) {
                        paintRightArea(g2, area, cardDimension);
                    }
                    break;
            }
        }

        var infoArea = getInfoArea(renderingArea);
        if (g2.getClip().intersects(infoArea)) {
            paintInfo(g2, infoArea, cardDimension);
        }

        // DEBUG
        g2.setColor(Color.RED);
        var ca = getCenterArea(renderingArea);
        var pa = getPlayerArea(renderingArea);
        var aa = getAcrossArea(renderingArea);
        var la = getLeftArea(renderingArea);
        var ra = getRightArea(renderingArea);
        var ia = getInfoArea(renderingArea);
        var ha = getHandArea(renderingArea);
        var da = getTeamDrawingArea(renderingArea);
        //  var cca = getCardArea(0, renderingArea);
        g2.drawRect(ca.x, ca.y, ca.width, ca.height);
        g2.drawRect(pa.x, pa.y, pa.width, pa.height);
        g2.drawRect(aa.x, aa.y, aa.width, aa.height);
        g2.drawRect(la.x, la.y, la.width, la.height);
        g2.drawRect(ra.x, ra.y, ra.width, ra.height);
        g2.drawRect(ia.x, ia.y, ia.width, ia.height);
        g2.drawRect(ha.x, ha.y, ha.width, ha.height);
        g2.drawRect(da.x, da.y, da.width, da.height);

        g2.setColor(Color.GREEN);
        g2.drawRect(renderingArea.x, renderingArea.y, renderingArea.width, renderingArea.height);
    }

    void resizePanel(ComponentEvent evt) {
        var area = getRenderingDimension();
        float scale = 530f / area.height;
        int x = round(530 / scale);
        int y = round(500 / scale);
        buttonAnnounce.setBounds(area.x + x, area.y + y,  90, 30);
    }
}
