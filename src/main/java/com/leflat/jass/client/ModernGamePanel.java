package com.leflat.jass.client;

import com.leflat.jass.common.BasePlayer;
import com.leflat.jass.common.Card;
import com.leflat.jass.server.PlayerLeftExpection;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RescaleOp;
import java.util.List;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.*;

public class ModernGamePanel extends JPanel implements MouseMotionListener {

    enum GameMode {
        TEAM_DRAWING, GAME, IDLE, ANIMATION
    }

    private final static boolean DEBUG = false;
    private final static Logger LOGGER = Logger.getLogger(OriginalUi.class.getName());
    private static final float ASPECT_RATIO = 630f / 530;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private static final int DOT_SIZE = 7;
    private final Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private GameMode gameMode = GameMode.IDLE;
    private final Set<Integer> drawnCards = new HashSet<>();
    private final List<Card> lastPlie = new ArrayList<>();
    private final Map<PlayerPosition, Card> playedCards = new HashMap<>();
    private final Map<PlayerPosition, Float> cardAngles = new HashMap<>();
    private int ourScore;
    private int theirScore;
    private int atoutColor = Card.COLOR_NONE;
    private boolean isInteractive = false;
    private final JButton buttonAnnounce = new JButton("Annoncer");
    private final ModernStatusPanel statusPanel = new ModernStatusPanel();
    private int hoveredCard = -1;
    private static final float ANIMATION_DURATION_S = 0.3f;//6; //0.3f;
    private static final int ANIMATION_FRAME_RATE = 20; //1;// 20;
    private final Map<PlayerPosition, Rectangle> animationPositions = new HashMap<>();
    private final Map<PlayerPosition, Point2D.Double> animationSteps = new HashMap<>();
    private int animationFrameNumber;
    private Timer animationTimer;
    private boolean announcementButtonPressed;
    private PlayerPosition atoutChooser = PlayerPosition.NONE;

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
        buttonAnnounce.addActionListener(this::pressButtonAnnouncement);
        add(statusPanel);

        addMouseMotionListener(this);
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
        repaint();
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
        while (animationTimer != null && animationTimer.isRunning()) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        playedCards.put(position, card);
        // random rotation disable until we fix the animation rendering
        cardAngles.put(position, 0f); //random.nextFloat() * 10 - 5f);
        repaint(toInt(getCenterArea(getRenderingDimension())));
    }

    public void collectPlie() {
        this.lastPlie.clear();
        this.lastPlie.addAll(playedCards.values());
        playedCards.clear();
        var renderingArea = getRenderingDimension();
        repaint(toInt(getInfoArea(renderingArea)).union(toInt(getCenterArea(renderingArea))));
    }

    public void setMode(GameMode mode) {
        this.gameMode = mode;
        repaint(toInt(getCenterArea(getRenderingDimension())));
    }

    public GameMode getMode() {
        return gameMode;
    }

    public void setAtoutColor(int atoutColor) {
        this.atoutColor = atoutColor;
        var renderingArea = getRenderingDimension();
        repaint(toInt(getInfoArea(renderingArea)));
    }

    public void hideAtout() {
        setAtoutColor(Card.COLOR_NONE);
    }

    public void setScores(int ourScore, int theirScore) {
        this.ourScore = ourScore;
        this.theirScore = theirScore;
        var renderingArea = getRenderingDimension();
        repaint(toInt(getInfoArea(renderingArea)));
    }

    public void drawCard(int cardPosition, Card card, PlayerPosition playerPosition) {
        drawnCards.add(cardPosition);
        try {
            players.get(playerPosition).setHand(Collections.singletonList(card));
        } catch (PlayerLeftExpection playerLeftExpection) {
            playerLeftExpection.printStackTrace();
        }
        repaint(toInt(getCenterArea(getRenderingDimension())));
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
            var cardDimension = getCardDimension(renderingArea);
            float card_x_step = hand.size() < 2 ? 0 : (handArea.width - cardDimension.width) / (float) (hand.size() - 1);
            int cardNumber = (int) floor((x - handArea.x) / card_x_step);
            return hand.get(min(cardNumber, hand.size() - 1));
        } else {
            var cardArea = getTeamDrawingArea(renderingArea);
            if (!cardArea.contains(x, y)) {
                return null;
            }
            var cardDimension = getCardDimension(renderingArea);
            float xStep = (cardArea.width - cardDimension.width) / 35f;
            int highNumber = (int) floor((x - cardArea.x) / xStep);
            int lowNumber = (int) floor((x - cardArea.x - cardDimension.width) / xStep);
            for (int number = min(highNumber, 35); number >= max(0, lowNumber); number--) {
                if (!drawnCards.contains(number)) {
                    return new Card(number);
                }
            }
        }
        return null;
    }

    public void collectCards(PlayerPosition position) {
        var renderingArea = getRenderingDimension();
        var centerArea = getCenterArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea);

        Rectangle2D.Float targetArea = getPlayerArea(position);
        Point animationTarget = new Point(round(targetArea.x + targetArea.width / 2),
                round(targetArea.y + targetArea.height / 2));

        animationFrameNumber = 0;
        int frameDurationMs = 1000 / ANIMATION_FRAME_RATE;
        int totalNbrSteps = round(ANIMATION_DURATION_S * 1000 / frameDurationMs);

        for (var playerPos : PlayerPosition.values()) {
            var cardPos = getPlayedCardPosition(playerPos, centerArea, cardDimension);
            int width, height;
            if (playerPos == PlayerPosition.MYSELF || playerPos == PlayerPosition.ACROSS) {
                width = cardDimension.width;
                height = cardDimension.height;
            } else {
                height = cardDimension.width;
                width = cardDimension.height;
            }
            int start_x = cardPos.x + width/2;
            int start_y = cardPos.y + height / 2;
            animationPositions.put(playerPos, new Rectangle(cardPos.x, cardPos.y, width, height));
            animationSteps.put(playerPos, new Point2D.Double((animationTarget.getX() - start_x) / totalNbrSteps,
             (animationTarget.getY() - start_y) / totalNbrSteps));
        }

        animationTimer = new Timer(frameDurationMs, actionEvent -> {
            Rectangle repaintArea = null;
            for (var playerPos : PlayerPosition.values()) {
                var pos = animationPositions.get(playerPos);
                var step = animationSteps.get(playerPos);
                repaintArea = repaintArea == null ? new Rectangle(pos) : repaintArea.union(pos);
                pos.x += step.x;
                pos.y += step.y;
                repaintArea = repaintArea.union(pos);
            }
            animationFrameNumber++;
            repaint(repaintArea);
            if (animationFrameNumber >= totalNbrSteps) {
                gameMode = GameMode.GAME;
                collectPlie();
                animationTimer.stop();
            }
        });
        gameMode = GameMode.ANIMATION;
        animationTimer.start();
    }

    public void setAnnouncementEnabled(boolean enabled) {
        buttonAnnounce.setEnabled(enabled);
        if (enabled) {
            announcementButtonPressed = false;
        }
    }

    public boolean announcementPressed() {
        return announcementButtonPressed;
    }

    public void displayStatusMessage(String message) {
        statusPanel.displayMessage(message);
    }

    public void setAtoutChooser(PlayerPosition position) {
        if (atoutChooser != PlayerPosition.NONE) {
            repaint(toInt(getPlayerArea(atoutChooser)));
        }
        atoutChooser = position;
        if (position != PlayerPosition.NONE) {
            repaint(toInt(getPlayerArea(position)));
        }
    }

    private void pressButtonAnnouncement(ActionEvent evt) {
        buttonAnnounce.setEnabled(false);
        announcementButtonPressed = true;
    }

    private void repaintPlayerArea(PlayerPosition position) {
        var playerArea = getPlayerArea(position);
        repaint(toInt(playerArea));
    }

    private Dimension getCardDimension(Rectangle2D.Float renderingArea) {
        float scale = renderingArea.height / 530f;
        float height = 96f * scale;
        float width = height / SwissCardImages.IMG_HEIGHT * SwissCardImages.IMG_WIDTH;
        var dim = new Dimension();
        dim.setSize(width, height);
        return dim;
    }

    private Rectangle2D.Float getHandArea(Rectangle2D.Float renderingArea) {
        var player = players.get(PlayerPosition.MYSELF);
        if (player == null) {
            return new Rectangle2D.Float(0, 0, 0, 0);
        }
        var hand = player.getHand();
        if (hand.size() == 0) {
            return new Rectangle2D.Float(0, 0, 0, 0);
        }
        var playerArea = getPlayerArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea);
        float hand_width = (hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width;
        float hand_x_offset = playerArea.x + (playerArea.width - hand_width) / 2;
        float hand_y_offset = playerArea.y + round(playerArea.height * 20f / 120f);
        return new Rectangle2D.Float(hand_x_offset, hand_y_offset, hand_width, cardDimension.height);
    }

    private Rectangle getCardArea(int number, Rectangle2D.Float area) {
        var handArea = getHandArea(area);
        var cardDimension = getCardDimension(area);
        var hand = players.get(PlayerPosition.MYSELF).getHand();
        assert number < hand.size();

        float card_x_step = hand.size() < 2 ? 0 : (handArea.width - cardDimension.width) / (float) (hand.size() - 1);
        return new Rectangle(round(handArea.x + number * card_x_step), round(handArea.y),
                round(cardDimension.width), round(cardDimension.height));
    }

    private Rectangle2D.Float getTeamDrawingArea(Rectangle2D.Float renderingArea) {
        var centerArea = getCenterArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea);
        return new Rectangle2D.Float(centerArea.x + centerArea.width / 10f,
                centerArea.y + (centerArea.height - cardDimension.height) / 2,
                centerArea.width * .8f,
                cardDimension.height);
    }

    private Rectangle2D.Float getRenderingDimension() {
        Dimension d = getSize();
        var area = new Rectangle2D.Float();
        if ((float) d.width / d.height > ASPECT_RATIO) {
            area.width = d.height * ASPECT_RATIO;
            area.height = d.height;
            area.x = (d.width - area.width) / 2;
            area.y = 0;
        } else {
            area.width = d.width;
            area.height = d.width / ASPECT_RATIO;
            area.x = 0;
            area.y = (d.height - area.height) / 2;
        }
        return area;
    }

    Rectangle2D.Float getCenterArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 390f / 630f;
        float height = renderingArea.height * 210f / 530f;
        float x = renderingArea.x + renderingArea.width * 120f / 630f;
        float y = renderingArea.y + renderingArea.height * 120f / 530f;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getPlayerArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 390f / 630f;
        float height = renderingArea.height * 120f / 530f;
        float x = renderingArea.x + renderingArea.width * 120f / 630f;
        float y = renderingArea.y + renderingArea.height * 330f / 530f;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getAcrossArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 390f / 630f;
        float height = renderingArea.height * 120f / 530f;
        float x = renderingArea.x + renderingArea.width * 120f / 630f;
        float y = renderingArea.y;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getLeftArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 120f / 630f;
        float height = renderingArea.height * 450f / 530f;
        return new Rectangle2D.Float(renderingArea.x, renderingArea.y, width, height);
    }

    Rectangle2D.Float getRightArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 120f / 630f;
        float height = renderingArea.height * 450f / 530f;
        float x = renderingArea.x + renderingArea.width * 510f / 630f;
        return new Rectangle2D.Float(x, renderingArea.y, width, height);
    }

    Rectangle2D.Float getInfoArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width;
        float height = renderingArea.height * 40f / 530f;
        float y = renderingArea.y + renderingArea.height * 450f / 530f;
        return new Rectangle2D.Float(renderingArea.x, y, width, height);
    }

    Point getPlayedCardPosition(PlayerPosition position, Rectangle2D.Float centerArea, Dimension cardDimension) {
        int x = 0, y = 0;
        switch (position) {
            case MYSELF:
                x = round(centerArea.x + (centerArea.width - cardDimension.width) / 2);
                y = round(centerArea.y + centerArea.height - cardDimension.height - cardDimension.height / 12f);
                break;
            case ACROSS:
                x = round(centerArea.x + (centerArea.width - cardDimension.width) / 2);
                y = round(centerArea.y + cardDimension.height / 12f);
                break;
            case LEFT:
                x = round(centerArea.x + cardDimension.width);
                y = round(centerArea.y + (centerArea.height - cardDimension.width) / 2f);
                break;
            case RIGHT:
                x = round(centerArea.x + centerArea.width - cardDimension.width - cardDimension.height);
                y = round(centerArea.y + (centerArea.height - cardDimension.width) / 2f);
                break;
        }
        return new Point(x, y);
    }

    void paintInfo(Graphics2D g2, Rectangle2D.Float infoArea, Dimension cardDimension) {
        float x_step = 30f * infoArea.width / 630f;
        float x_offset = infoArea.x + 120f * infoArea.width / 630f;
        float y_offset = infoArea.y + 5f * infoArea.height / 40f;
        for (int i = 0; i < lastPlie.size(); i++) {
            int card_x = round(x_offset + x_step * i);
            g2.drawImage(SwissCardImages.getImage(lastPlie.get(i)),
                    card_x, round(y_offset),
                    card_x + cardDimension.width, round(y_offset + 35f / 40f * infoArea.height),
                    0, 0,
                    SwissCardImages.IMG_WIDTH, round(35f / 96f * SwissCardImages.IMG_HEIGHT),
                    this);
        }
        float last_plie_x = 20f / 40 * infoArea.height;
        g2.drawString("Dernière plie:", infoArea.x + last_plie_x,
                infoArea.y + last_plie_x);
        if (atoutColor != Card.COLOR_NONE) {
            float atout_x = 340f / 40f * infoArea.height;
            g2.drawString("Atout:", infoArea.x + atout_x,
                    infoArea.y + last_plie_x);
            int stringWidth = g2.getFontMetrics().stringWidth("Atout:  ");
            int colorSize = round(15f / 40f * infoArea.height);
            g2.drawImage(SwissCardImages.getColorImage(atoutColor),
                    round(infoArea.x + atout_x + stringWidth),
                    round(infoArea.y + 8f / 40f * infoArea.height),
                    colorSize, colorSize, this);
        }
        float score_label_x = 420f / 40f * infoArea.height;
        float our_score_y = 13f / 40f * infoArea.height;
        g2.drawString("Nous:", infoArea.x + score_label_x,
                infoArea.y + our_score_y);
        float their_score_y = 27f / 40f * infoArea.height;
        g2.drawString("Eux:", infoArea.x + score_label_x,
                infoArea.y + their_score_y);
        float score_x = 470f / 40f * infoArea.height;
        g2.drawString(String.valueOf(ourScore), infoArea.x + score_x,
                infoArea.y + our_score_y);
        g2.drawString(String.valueOf(theirScore), infoArea.x + score_x,
                infoArea.y + their_score_y);
    }

    void paintCenter(Graphics2D g2, Rectangle2D.Float centerArea, Dimension cardDimension) {
        // draw carpet
        var color = g2.getColor();
        g2.setColor(CARPET_COLOR);
        g2.fillRoundRect(round(centerArea.x), round(centerArea.y),
                round(centerArea.width), round(centerArea.height), round(centerArea.width / 20), round(centerArea.width / 20));
        g2.setColor(color);
        float scale = (float) cardDimension.height / SwissCardImages.IMG_HEIGHT;

        if (gameMode == GameMode.TEAM_DRAWING) {
            for (int i = 0; i < 36; i++) {
                float card_x_offset = centerArea.width / 10f;
                float card_x_step = (centerArea.width - 2 * card_x_offset - cardDimension.width) / 35.0f;
                int card_y_offset = round((centerArea.height - cardDimension.height) / 2 + centerArea.y);
                if (!drawnCards.contains(i)) {
                    g2.drawImage(SwissCardImages.getBackImage(), round(centerArea.x + card_x_offset + i * card_x_step),
                            card_y_offset, cardDimension.width, cardDimension.height, this);
                }
            }
        } else if (gameMode == GameMode.GAME) {
            for (var entry : playedCards.entrySet()) {
                var position = getPlayedCardPosition(entry.getKey(), centerArea, cardDimension);
                var xform = new AffineTransform();
                xform.translate(position.x, position.y);
                xform.scale(scale, scale);
                switch (entry.getKey()) {
                    case LEFT:
                        xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(90 + cardAngles.get(PlayerPosition.LEFT)));
                        xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
                        break;
                    case RIGHT:
                        xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
                        xform.rotate(toRadians(-90 + cardAngles.get(PlayerPosition.RIGHT)));
                        xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
                        break;
                }
                g2.drawRenderedImage(SwissCardImages.getImage(entry.getValue()), xform);
            }
        }
    }

    void paintPlayerArea(Graphics2D g2, Rectangle2D.Float playerArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.MYSELF);
        var hand = player.getHand();

        // cards
        float hand_width = (hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width;
        float hand_x_offset = playerArea.x + (playerArea.width - hand_width) / 2;
        int hand_y_offset = round(playerArea.y + playerArea.height * 20f / 120f);
        float card_x_step = hand.size() < 2 ? 0 : (hand_width - cardDimension.width) / (float) (hand.size() - 1);
        for (int i = 0; i < hand.size(); i++) {
            if (i == hoveredCard && isInteractive) {
                var image = SwissCardImages.getImage(hand.get(i));
                RescaleOp op = new RescaleOp(0.7f, 0, null);
                var darken = op.filter(image, null);
                g2.drawImage(darken,
                        round(hand_x_offset + i * card_x_step), hand_y_offset,
                        cardDimension.width, cardDimension.height, this);
            } else {
                g2.drawImage(SwissCardImages.getImage(hand.get(i)),
                        round(hand_x_offset + i * card_x_step), hand_y_offset,
                        cardDimension.width, cardDimension.height, this);
            }
        }

        // name
        float nameX = playerArea.x + playerArea.width * 30f / 390f;
        float nameY = playerArea.y + playerArea.height * 15f / 120f;
        g2.drawString(player.getName(), nameX, nameY);

        if (atoutChooser == PlayerPosition.MYSELF) {
            int width = g2.getFontMetrics().stringWidth(player.getName());
            g2.fillOval(round(nameX + 8 + width), round(nameY - 8), DOT_SIZE, DOT_SIZE);
        }
    }

    void paintAcrossArea(Graphics2D g2, Rectangle2D.Float topArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.ACROSS);
        var hand = player.getHand();
        var hand_width = (hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width;
        var hand_x_offset = topArea.x + (topArea.width - hand_width) / 2;
        var hand_y_offset = round(topArea.y + topArea.height * 20f / 120f);
        var card_x_step = hand.size() < 2 ? 0 : (hand_width - cardDimension.width) / (float) (hand.size() - 1);
        for (int i = hand.size() - 1; i >= 0; i--) {
            g2.drawImage(SwissCardImages.getImage(hand.get(i)),
                    round(hand_x_offset + i * card_x_step), hand_y_offset,
                    cardDimension.width, cardDimension.height, this);
        }

        // name
        float nameX = topArea.x + topArea.width * 30f / 390f;
        float nameY = topArea.y + topArea.height * 15f / 120f;
        g2.drawString(player.getName(), nameX, nameY);

        if (atoutChooser == PlayerPosition.ACROSS) {
            int width = g2.getFontMetrics().stringWidth(player.getName());
            g2.fillOval(round(nameX + 8 + width), round(nameY - 8), DOT_SIZE, DOT_SIZE);
        }
    }

    void paintLeftArea(Graphics2D g2, Rectangle2D.Float leftArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.LEFT);
        var hand = player.getHand();

        float scale = (float) cardDimension.height / SwissCardImages.IMG_HEIGHT;
        float hand_height = (hand.size() - 1) * cardDimension.width / 2.1f + (float) cardDimension.getWidth();
        float card_y_step = hand.size() < 2 ? 0 : (hand_height - cardDimension.width) / (float) (hand.size() - 1);
        var hand_x_offset = leftArea.x + (leftArea.width - cardDimension.height) / 2;
        var hand_y_offset = leftArea.y + (leftArea.height - hand_height) / 2;
        for (int i = hand.size() - 1; i >= 0; i--) {
            var xform = new AffineTransform();
            xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
            xform.scale(scale, scale);
            xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
            xform.rotate(toRadians(90));
            xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(SwissCardImages.getImage(hand.get(i)), xform);
        }

        // name
        float nameX = leftArea.x + leftArea.width * 20f / 120f;
        float nameY = leftArea.y + min(leftArea.height * 90f / 450f, hand_y_offset - cardDimension.width / 3f);
        g2.drawString(player.getName(), nameX, nameY);
        if (atoutChooser == PlayerPosition.LEFT) {
            g2.fillOval(round(nameX), round(nameY + 7), DOT_SIZE, DOT_SIZE);
        }
    }

    void paintRightArea(Graphics2D g2, Rectangle2D.Float rightArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.RIGHT);
        var hand = player.getHand();

        var scale = (float) cardDimension.height / SwissCardImages.IMG_HEIGHT;
        var hand_height = (hand.size() - 1) * cardDimension.width / 2.1f + (float)cardDimension.getWidth();
        var card_y_step = hand.size() < 2 ? 0 : (hand_height - cardDimension.width) / (float) (hand.size() - 1);
        var hand_x_offset = rightArea.x + (rightArea.width - cardDimension.height) / 2;
        var hand_y_offset = rightArea.y + (rightArea.height - hand_height) / 2;
        for (int i = 0; i < hand.size(); i++) {
            var xform = new AffineTransform();
            xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
            xform.scale(scale, scale);
            xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
            xform.rotate(toRadians(-90));
            xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(SwissCardImages.getImage(hand.get(i)), xform);
        }

        // name
        float nameX = rightArea.x + rightArea.width * 20f / 120f;
        float nameY = rightArea.y + min(round(rightArea.height * 90f / 450f), hand_y_offset - cardDimension.width / 3f);
        g2.drawString(player.getName(), nameX, nameY);

        if (atoutChooser == PlayerPosition.RIGHT) {
            g2.fillOval(round(nameX), round(nameY + 7), DOT_SIZE, DOT_SIZE);
        }
    }

    void paintAnimation(Graphics2D g2, Rectangle2D.Float renderingArea, Dimension cardDimension) {
        float scale = (float) cardDimension.height / SwissCardImages.IMG_HEIGHT;
        for (var playerPosition : players.keySet()) {
            var card = playedCards.get(playerPosition);
            var pos = animationPositions.get(playerPosition);

            var xform = new AffineTransform();
            xform.translate(pos.x, pos.y);
            xform.scale(scale, scale);
            switch (playerPosition) {
                case LEFT:
                    xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
                    xform.rotate(toRadians(90 + cardAngles.get(PlayerPosition.LEFT)));
                    xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
                    break;
                case RIGHT:
                    xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
                    xform.rotate(toRadians(-90 + cardAngles.get(PlayerPosition.RIGHT)));
                    xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
                    break;
            }
            g2.drawRenderedImage(SwissCardImages.getImage(card), xform);
            if (DEBUG) {
                var currentColor = g2.getColor();
                g2.setColor(Color.CYAN);
                g2.drawRect(pos.x, pos.y, pos.width, pos.height);
                g2.setColor(currentColor);
            }
         //   g2.drawImage(SwissCardImages.getImage(card), (int) round(pos.getX()), (int) round(pos.getY()), cardDimension.width, cardDimension.height, this);
        }
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

        if (gameMode == GameMode.ANIMATION) {
            paintAnimation(g2, renderingArea, cardDimension);
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

        if (DEBUG) {
            g2.setColor(Color.RED);
            var ca = toInt(getCenterArea(renderingArea));
            var pa = toInt(getPlayerArea(renderingArea));
            var aa = toInt(getAcrossArea(renderingArea));
            var la = toInt(getLeftArea(renderingArea));
            var ra = toInt(getRightArea(renderingArea));
            var ia = toInt(getInfoArea(renderingArea));
            var ha = toInt(getHandArea(renderingArea));
            var da = toInt(getTeamDrawingArea(renderingArea));
            if (players.containsKey(PlayerPosition.MYSELF) && players.get(PlayerPosition.MYSELF).getHand().size() > 0) {
                var cca = getCardArea(0, renderingArea);
                g2.drawRect(cca.x, cca.y, cca.width, cca.height);
            }
            g2.drawRect(ca.x, ca.y, ca.width, ca.height);
            g2.drawRect(pa.x, pa.y, pa.width, pa.height);
            g2.drawRect(aa.x, aa.y, aa.width, aa.height);
            g2.drawRect(la.x, la.y, la.width, la.height);
            g2.drawRect(ra.x, ra.y, ra.width, ra.height);
            g2.drawRect(ia.x, ia.y, ia.width, ia.height);
            g2.drawRect(ha.x, ha.y, ha.width, ha.height);
            g2.drawRect(da.x, da.y, da.width, da.height);

            g2.setColor(Color.GREEN);
            g2.drawRect(round(renderingArea.x), round(renderingArea.y),
                    round(renderingArea.width), round(renderingArea.height));

            g2.setColor(Color.BLUE);
            var clip = g2.getClip().getBounds();
            g2.drawRect(clip.x, clip.y, clip.width - 1, clip.height - 1);
        }
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (!isInteractive || gameMode != GameMode.GAME) {
            return;
        }
        var card = getCard(mouseEvent.getX(), mouseEvent.getY());
        var index = card == null ? -1 : players.get(PlayerPosition.MYSELF).getHand().indexOf(card);
        if (index < 0) {
            if (hoveredCard != -1) {
                repaint(getCardArea(hoveredCard, getRenderingDimension()));
            }
            hoveredCard = -1;
            return;
        }
        if (index != hoveredCard) {
            var area = getCardArea(index, getRenderingDimension());
            if (hoveredCard >= 0) {
                area = area.union(getCardArea(hoveredCard, getRenderingDimension()));
            }
            hoveredCard = index;
            repaint(area);
        }
    }

    void resizePanel(ComponentEvent evt) {
        var area = getRenderingDimension();
        float scale = 530f / area.height;
        float x = 530 / scale;
        float panelHeight = 40 / scale;
        var size = buttonAnnounce.getPreferredSize();
        float y = 490 / scale + (panelHeight - size.height) / 2;
        buttonAnnounce.setBounds(round(area.x + x), round(area.y + y), size.width, size.height);

        statusPanel.setBounds(round(area.x), round(area.y + 490 / scale),
                round(400 / scale), round(40 / scale));
    }

    Rectangle toInt(Rectangle2D.Float rect) {
        return new Rectangle(round(rect.x), round(rect.y), round(rect.width), round(rect.height));
    }

    Rectangle2D.Float getPlayerArea(PlayerPosition position) {
        var renderingArea = getRenderingDimension();
        switch(position) {
            case MYSELF:
                return getPlayerArea(renderingArea);
            case ACROSS:
                return getAcrossArea(renderingArea);
            case LEFT:
                return getLeftArea(renderingArea);
            case RIGHT:
                return getRightArea(renderingArea);
        }
        return null;
    }
}
