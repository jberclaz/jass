package com.leflat.jass.client;

import com.leflat.jass.common.Announcement;
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

/**
 * This class implements the central canvas on the player client, including the pad where cards are played,
 * the players hands and the info area underneath the main player's cards. It also contains the logic for
 * interactive cards selection.
 * It must only be used from the Swing thread: ModernUi runs the changes there, with runAfterAnimation.
 */
public class ModernGamePanel extends JPanel implements MouseMotionListener {

    enum GameMode {
        TEAM_DRAWING, GAME, IDLE, ANIMATION
    }

    private final static boolean DEBUG = false;
    private final static Logger LOGGER = Logger.getLogger(OriginalUi.class.getName());
    private static final int DEFAULT_WIDTH = 630;
    private static final int DEFAULT_HEIGHT = 530;
    private static final int DEFAULT_CENTER_WIDTH = 390;
    private static final int DEFAULT_CENTER_HEIGHT = 210;
    private static final int DEFAULT_CENTER_LEFT = 120;
    private static final int DEFAULT_CENTER_TOP = 120;
    private static final float ASPECT_RATIO = (float) DEFAULT_WIDTH / DEFAULT_HEIGHT;
    private static final Color CARPET_COLOR = new Color(51, 102, 0);
    private static final float CARD_SCALE = (float) CardImages.IMG_HEIGHT / SwissCardImages.IMG_HEIGHT;
    private static final Color ANNOUNCEMENT_COLOR = new Color(0, 70, 140);
    private static final Color TRUMP_BADGE_COLOR = new Color(110, 60, 0);
    private static final Color CROWN_COLOR = new Color(235, 180, 30);

    private final Map<PlayerPosition, BasePlayer> players = new HashMap<>();
    private GameMode gameMode = GameMode.IDLE;
    private final Set<Integer> drawnCards = new HashSet<>();
    private final List<Card> lastPlie = new ArrayList<>();
    // in the order the cards were played, which is also the order of the last plie
    private final Map<PlayerPosition, Card> playedCards = new LinkedHashMap<>();
    private final Map<PlayerPosition, Double> cardAngles = new HashMap<>();
    private int ourScore;
    private int theirScore;
    private int atoutColor = Card.COLOR_NONE;
    private boolean isInteractive = false;
    private final JButton buttonAnnounce = new JButton("Annoncer");
    private final ModernStatusPanel statusPanel = new ModernStatusPanel();
    private int hoveredCard = -1;
    private static final float COLLECTION_DURATION_S = 0.33f;
    private static final float PLAY_DURATION_S = 0.2f;
    private static final int ANIMATION_FRAME_RATE = 60;
    // margin around the repainted cards, for their antialiased edges
    private static final int REPAINT_MARGIN = 2;
    private final Map<PlayerPosition, Rectangle2D.Double> playedCardsPositions = new HashMap<>();

    // a played card moving from (x0, y0, angle0) to (x1, y1, angle1): its center relative to the carpet, in default size
    private record CardMove(PlayerPosition position, double x0, double y0, double angle0, double x1, double y1,
                            double angle1) {
    }

    private final List<CardMove> cardMoves = new ArrayList<>();
    private float animationDurationS;
    // the played cards are drawn above the hands, the collected ones under them
    private boolean animationAboveHands;
    private Runnable animationEnd;
    private long animationStartNs;
    private Timer animationTimer;
    // where the card chosen by the player was in their hand, relative to the carpet
    private Point2D.Double chosenCardOrigin;
    private Card chosenCard;
    // changes received during the animation, applied when it ends
    private final Queue<Runnable> pendingUpdates = new ArrayDeque<>();
    // read by the client's network thread
    private volatile boolean announcementButtonPressed;
    // the trump choice, shown next to the players' names: who is choosing, then who chose and who passed
    private PlayerPosition trumpChooser = PlayerPosition.NONE;
    private PlayerPosition trumpMaker = PlayerPosition.NONE;
    private PlayerPosition trumpPasser = PlayerPosition.NONE;
    private final Random random = new Random();
    // the announcements of the hand, shown next to each player until the next hand
    private final Map<PlayerPosition, List<Announcement>> announcements = new HashMap<>();

    public ModernGamePanel() {
        super();
        drawnCards.clear();
        setDoubleBuffered(true);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
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
        playedCards.clear();
        announcements.clear();
        trumpChooser = PlayerPosition.NONE;
        trumpMaker = PlayerPosition.NONE;
        trumpPasser = PlayerPosition.NONE;
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

    /**
     * Runs a change of the panel now or, during the animation of the plie collection, when it ends, so that the
     * changes are shown in the order they were received.
     */
    public void runAfterAnimation(Runnable update) {
        if (gameMode == GameMode.ANIMATION || !pendingUpdates.isEmpty()) {
            pendingUpdates.add(update);
            return;
        }
        update.run();
    }

    /** Plays a card: it moves from the player's hand to the carpet. */
    public void setPlayedCard(PlayerPosition position, Card card) {
        var origin = position == PlayerPosition.MYSELF && card.equals(chosenCard) ? chosenCardOrigin
                : getHandCenter(position);
        placeCardOnCarpet(position, card);
        var cardCenter = getPlayedCardCenter(position);
        startAnimation(List.of(new CardMove(position, origin.x, origin.y, 0, cardCenter.x, cardCenter.y,
                cardAngles.get(position))), PLAY_DURATION_S, true, () -> {
        });
    }

    /** Remembers where the card the player clicked is in their hand, where it will start from when played. */
    public void setChosenCard(Card card) {
        var renderingArea = getRenderingDimension();
        var hand = players.get(PlayerPosition.MYSELF).getHand();
        int index = hand.indexOf(card);
        if (index < 0) {
            return;
        }
        var cardArea = getCardArea(index, renderingArea);
        var centerArea = getCenterArea(renderingArea);
        var scale = centerArea.width / DEFAULT_CENTER_WIDTH;
        chosenCard = card;
        chosenCardOrigin = new Point2D.Double((cardArea.getCenterX() - centerArea.x) / scale,
                (cardArea.getCenterY() - centerArea.y) / scale);
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
        hoveredCard = -1;
    }

    public boolean isInsidePlayerCardsArea(int x, int y) {
        return getHandArea(getRenderingDimension()).contains(x, y);
    }

    public Card getCard(int x, int y) {
        if (!isInteractive) {
            return null;
        }
        var renderingArea = getRenderingDimension();
        var scale = renderingArea.width / DEFAULT_WIDTH;
        if (gameMode == GameMode.GAME) {
            var handArea = getHandArea(renderingArea);
            if (!handArea.contains(x, y)) {
                return null;
            }
            var hand = players.get(PlayerPosition.MYSELF).getHand();
            var cardDimension = getCardDimension(scale);
            float card_x_step = hand.size() < 2 ? 0 : (handArea.width - cardDimension.width) / (float) (hand.size() - 1);
            int cardNumber = (int) floor((x - handArea.x) / card_x_step);
            return hand.get(min(cardNumber, hand.size() - 1));
        } else if (gameMode == GameMode.TEAM_DRAWING) {
            var cardArea = getTeamDrawingArea(renderingArea);
            if (!cardArea.contains(x, y)) {
                return null;
            }
            var cardDimension = getCardDimension(scale);
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
        var target = getHandCenter(position);
        var moves = new ArrayList<CardMove>();
        for (var playerPos : playedCards.keySet()) {
            var cardBbox = playedCardsPositions.get(playerPos);
            double angle = cardAngles.get(playerPos);
            moves.add(new CardMove(playerPos, cardBbox.getCenterX(), cardBbox.getCenterY(), angle, target.x, target.y,
                    angle));
        }
        startAnimation(moves, COLLECTION_DURATION_S, false, this::collectPlie);
    }

    private void startAnimation(List<CardMove> moves, float durationS, boolean aboveHands, Runnable end) {
        if (gameMode == GameMode.ANIMATION) {
            // only when the panel is changed directly, without runAfterAnimation
            completeAnimation();
        }
        cardMoves.clear();
        cardMoves.addAll(moves);
        animationDurationS = DEBUG ? durationS * 10 : durationS;
        animationAboveHands = aboveHands;
        animationEnd = end;
        moveCards(0);
        animationStartNs = System.nanoTime();
        animationTimer = new Timer(1000 / ANIMATION_FRAME_RATE, actionEvent -> animate());
        gameMode = GameMode.ANIMATION;
        animationTimer.start();
    }

    private void animate() {
        // from the elapsed time, so that late frames don't slow the animation down
        double t = min(1, (System.nanoTime() - animationStartNs) / 1e9 / animationDurationS);
        if (t < 1) {
            moveCards(t);
            return;
        }
        completeAnimation();
        // stops if one of the changes starts a new animation
        while (!pendingUpdates.isEmpty() && gameMode != GameMode.ANIMATION) {
            pendingUpdates.poll().run();
        }
    }

    private void completeAnimation() {
        animationTimer.stop();
        moveCards(1);
        gameMode = GameMode.GAME;
        animationEnd.run();
    }

    /** Moves the cards to where they are at the time t of the animation, from 0 to 1. */
    private void moveCards(double t) {
        // quadratic ease-out: the cards slow down as they arrive, starting at twice the average speed
        double progress = 1 - pow(1 - t, 2);
        // smoothstep: the cards turn slowly at first and settle at their angle as they arrive
        double rotation = t * t * (3 - 2 * t);
        Rectangle repaintArea = null;
        for (var move : cardMoves) {
            var previousArea = getPlayedCardScreenArea(playedCardsPositions.get(move.position()));
            setPlayedCardGeometry(move.position(), move.x0() + (move.x1() - move.x0()) * progress,
                    move.y0() + (move.y1() - move.y0()) * progress,
                    move.angle0() + (move.angle1() - move.angle0()) * rotation);
            var area = previousArea.union(getPlayedCardScreenArea(playedCardsPositions.get(move.position())));
            repaintArea = repaintArea == null ? area : repaintArea.union(area);
        }
        if (repaintArea != null) {
            repaint(repaintArea);
        }
    }

    /** The middle of a player's area, relative to the carpet, in default size. */
    private Point2D.Double getHandCenter(PlayerPosition position) {
        Rectangle2D.Float area = getPlayerAreaOriginal(position);
        return new Point2D.Double(area.x + area.width / 2 - DEFAULT_CENTER_LEFT,
                area.y + area.height / 2 - DEFAULT_CENTER_TOP);
    }

    /** The screen area of a card on the carpet, whose bounding box is relative to the carpet, in default size. */
    private Rectangle getPlayedCardScreenArea(Rectangle2D.Double cardBbox) {
        var centerArea = getCenterArea(getRenderingDimension());
        var scale = centerArea.width / DEFAULT_CENTER_WIDTH;
        var area = toInt(new Rectangle2D.Float((float) (centerArea.x + cardBbox.x * scale),
                (float) (centerArea.y + cardBbox.y * scale), (float) (cardBbox.width * scale),
                (float) (cardBbox.height * scale)));
        area.grow(REPAINT_MARGIN, REPAINT_MARGIN);
        return area;
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

    public void addAnnouncements(PlayerPosition position, List<Announcement> newAnnouncements) {
        // the stoeck comes later than the other announcements: it is added to them
        var all = new ArrayList<>(announcements.getOrDefault(position, Collections.emptyList()));
        all.addAll(newAnnouncements);
        announcements.put(position, all);
        repaintPlayerArea(position);
    }

    public void displayStatusMessage(String message) {
        statusPanel.displayMessage(message);
    }

    /** The player is choosing the trump. */
    public void setTrumpChooser(PlayerPosition position) {
        trumpChooser = position;
        trumpMaker = PlayerPosition.NONE;
        repaint();
    }

    /** The player passed: their partner chooses the trump. */
    public void setTrumpPassed(PlayerPosition position) {
        trumpPasser = position;
        trumpChooser = position.partner();
        trumpMaker = PlayerPosition.NONE;
        repaint();
    }

    /** The trump is chosen, by the player asked first or, if they passed, by their partner. */
    public void setTrumpChosen(PlayerPosition firstPosition, boolean chosenOnFirstTurn) {
        trumpChooser = PlayerPosition.NONE;
        trumpMaker = chosenOnFirstTurn ? firstPosition : firstPosition.partner();
        trumpPasser = chosenOnFirstTurn ? PlayerPosition.NONE : firstPosition;
        repaint();
    }

    private void placeCardOnCarpet(PlayerPosition position, Card card) {
        playedCards.put(position, card);
        var cardCenter = getPlayedCardCenter(position);
        setPlayedCardGeometry(position, cardCenter.x, cardCenter.y, toRadians(random.nextFloat() * 10 - 5f));
    }

    /** Places a played card by its center, relative to the carpet in default size, and its angle. */
    private void setPlayedCardGeometry(PlayerPosition position, double centerX, double centerY, double angle) {
        cardAngles.put(position, angle);
        float width, height;
        if (position == PlayerPosition.MYSELF || position == PlayerPosition.ACROSS) {
            width = SwissCardImages.IMG_WIDTH * CARD_SCALE;
            height = CardImages.IMG_HEIGHT;
        } else {
            height = SwissCardImages.IMG_WIDTH * CARD_SCALE;
            width = CardImages.IMG_HEIGHT;
        }
        var abscos = abs(cos(angle));
        var abssin = abs(sin(angle));
        var rotatedWidth = width * abscos + height * abssin;
        var rotatedHeight = width * abssin + height * abscos;
        playedCardsPositions.put(position, new Rectangle2D.Double(centerX - rotatedWidth / 2, centerY - rotatedHeight / 2, rotatedWidth, rotatedHeight));
    }

    private void pressButtonAnnouncement(ActionEvent evt) {
        buttonAnnounce.setEnabled(false);
        announcementButtonPressed = true;
    }

    private void repaintPlayerArea(PlayerPosition position) {
        var playerArea = getPlayerArea(position);
        repaint(toInt(playerArea));
    }

    private Dimension getCardDimension(float scale) {
        float height = (float) CardImages.IMG_HEIGHT * scale;
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
        if (hand.isEmpty()) {
            return new Rectangle2D.Float(0, 0, 0, 0);
        }
        var playerArea = getPlayerArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea.width / DEFAULT_WIDTH);
        float hand_width = (hand.size() - 1) * cardDimension.width / 2.1f + cardDimension.width;
        float hand_x_offset = playerArea.x + (playerArea.width - hand_width) / 2;
        float hand_y_offset = playerArea.y + round(playerArea.height * 20f / 120f);
        return new Rectangle2D.Float(hand_x_offset, hand_y_offset, hand_width, cardDimension.height);
    }

    private Rectangle getCardArea(int number, Rectangle2D.Float area) {
        var handArea = getHandArea(area);
        var cardDimension = getCardDimension(area.width / DEFAULT_WIDTH);
        var hand = players.get(PlayerPosition.MYSELF).getHand();
        assert number < hand.size();

        float card_x_step = hand.size() < 2 ? 0 : (handArea.width - cardDimension.width) / (float) (hand.size() - 1);
        return new Rectangle(round(handArea.x + number * card_x_step), round(handArea.y),
                cardDimension.width, cardDimension.height);
    }

    private Rectangle2D.Float getTeamDrawingArea(Rectangle2D.Float renderingArea) {
        var centerArea = getCenterArea(renderingArea);
        var cardDimension = getCardDimension(renderingArea.width / DEFAULT_WIDTH);
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
        float width = renderingArea.width * DEFAULT_CENTER_WIDTH / DEFAULT_WIDTH;
        float height = renderingArea.height * DEFAULT_CENTER_HEIGHT / DEFAULT_HEIGHT;
        float x = renderingArea.x + renderingArea.width * DEFAULT_CENTER_LEFT / DEFAULT_WIDTH;
        float y = renderingArea.y + renderingArea.height * DEFAULT_CENTER_TOP / DEFAULT_HEIGHT;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getPlayerArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * DEFAULT_CENTER_WIDTH / DEFAULT_WIDTH;
        float height = renderingArea.height * 120f / DEFAULT_HEIGHT;
        float x = renderingArea.x + renderingArea.width * 120f / DEFAULT_WIDTH;
        float y = renderingArea.y + renderingArea.height * 330f / DEFAULT_HEIGHT;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getAcrossArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * DEFAULT_CENTER_WIDTH / DEFAULT_WIDTH;
        float height = renderingArea.height * 120f / DEFAULT_HEIGHT;
        float x = renderingArea.x + renderingArea.width * 120f / DEFAULT_WIDTH;
        float y = renderingArea.y;
        return new Rectangle2D.Float(x, y, width, height);
    }

    Rectangle2D.Float getLeftArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 120f / DEFAULT_WIDTH;
        float height = renderingArea.height * 450f / DEFAULT_HEIGHT;
        return new Rectangle2D.Float(renderingArea.x, renderingArea.y, width, height);
    }

    Rectangle2D.Float getRightArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width * 120f / DEFAULT_WIDTH;
        float height = renderingArea.height * 450f / DEFAULT_HEIGHT;
        float x = renderingArea.x + renderingArea.width * 510f / DEFAULT_WIDTH;
        return new Rectangle2D.Float(x, renderingArea.y, width, height);
    }

    Rectangle2D.Float getInfoArea(Rectangle2D.Float renderingArea) {
        float width = renderingArea.width;
        float height = renderingArea.height * 40f / DEFAULT_HEIGHT;
        float y = renderingArea.y + renderingArea.height * 450f / DEFAULT_HEIGHT;
        return new Rectangle2D.Float(renderingArea.x, y, width, height);
    }

    Point2D.Float getPlayedCardCenter(PlayerPosition position) {
        float x = 0, y = 0;
        switch (position) {
            case MYSELF:
                x = DEFAULT_CENTER_WIDTH / 2f;
                y = DEFAULT_CENTER_HEIGHT - CardImages.IMG_HEIGHT * 7 / 12f;
                break;
            case ACROSS:
                x = DEFAULT_CENTER_WIDTH / 2f;
                y = CardImages.IMG_HEIGHT * 7 / 12f;
                break;
            case LEFT:
                x = CardImages.IMG_WIDTH * 4 / 3f;
                y = DEFAULT_CENTER_HEIGHT / 2f;
                break;
            case RIGHT:
                x = round(DEFAULT_CENTER_WIDTH - CardImages.IMG_WIDTH * 4 / 3f);
                y = DEFAULT_CENTER_HEIGHT / 2f;
                break;
        }
        return new Point2D.Float(x, y);
    }

    void paintInfo(Graphics2D g2, Rectangle2D.Float infoArea, Dimension cardDimension) {
        float x_step = 30f * infoArea.width / DEFAULT_WIDTH;
        float x_offset = infoArea.x + 120f * infoArea.width / DEFAULT_WIDTH;
        float y_offset = infoArea.y + 5f * infoArea.height / 40f;
        for (int i = 0; i < lastPlie.size(); i++) {
            int card_x = round(x_offset + x_step * i);
            g2.drawImage(SwissCardImages.getImage(lastPlie.get(i)),
                    card_x, round(y_offset),
                    card_x + cardDimension.width, round(y_offset + 35f / 40f * infoArea.height),
                    0, 0,
                    SwissCardImages.IMG_WIDTH, round(35f / CardImages.IMG_HEIGHT * SwissCardImages.IMG_HEIGHT),
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

    void paintCenter(Graphics2D g2, Rectangle2D.Float centerArea, float scale) {
        // draw carpet
        var color = g2.getColor();
        g2.setColor(CARPET_COLOR);
        g2.fillRoundRect(round(centerArea.x), round(centerArea.y),
                round(centerArea.width), round(centerArea.height), round(centerArea.width / 20), round(centerArea.width / 20));
        g2.setColor(color);

        if (gameMode == GameMode.TEAM_DRAWING) {
            var cardDimension = getCardDimension(centerArea.width / DEFAULT_CENTER_WIDTH);
            for (int i = 0; i < Card.DECK_SIZE; i++) {
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
                paintPlayedCard(g2, entry.getKey(), centerArea, scale);
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
        float nameX = playerArea.x + playerArea.width * 30f / DEFAULT_CENTER_WIDTH;
        float nameY = playerArea.y + playerArea.height * 15f / 120f;
        g2.drawString(player.getName(), nameX, nameY);

        float gap = g2.getFontMetrics().stringWidth("   ");
        float x = nameX + g2.getFontMetrics().stringWidth(player.getName()) + gap;
        float badgeWidth = getTrumpBadgeWidth(g2, PlayerPosition.MYSELF);
        if (badgeWidth > 0) {
            paintTrumpBadge(g2, PlayerPosition.MYSELF, x, nameY);
            x += badgeWidth + gap;
        }
        paintAnnouncements(g2, PlayerPosition.MYSELF, x, nameY, false, Float.MAX_VALUE);
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
        float nameX = topArea.x + topArea.width * 30f / DEFAULT_CENTER_WIDTH;
        float nameY = topArea.y + topArea.height * 15f / 120f;
        g2.drawString(player.getName(), nameX, nameY);

        float gap = g2.getFontMetrics().stringWidth("   ");
        float x = nameX + g2.getFontMetrics().stringWidth(player.getName()) + gap;
        float badgeWidth = getTrumpBadgeWidth(g2, PlayerPosition.ACROSS);
        if (badgeWidth > 0) {
            paintTrumpBadge(g2, PlayerPosition.ACROSS, x, nameY);
            x += badgeWidth + gap;
        }
        paintAnnouncements(g2, PlayerPosition.ACROSS, x, nameY, false, Float.MAX_VALUE);
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
            xform.rotate(toRadians(-90));
            xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(SwissCardImages.getImage(hand.get(i)), xform);
        }

        // name
        float nameX = leftArea.x + leftArea.width * 20f / 120f;
        float nameY = min(hand_y_offset - cardDimension.height / 10f, leftArea.y + leftArea.height / 4);
        g2.drawString(player.getName(), nameX, nameY);
        paintSideTrumpBadge(g2, PlayerPosition.LEFT, player.getName(), nameX, nameY, leftArea);
        paintAnnouncements(g2, PlayerPosition.LEFT, nameX, hand_y_offset + hand_height + g2.getFontMetrics().getHeight(),
                true, leftArea.x + leftArea.width - 4);
    }

    void paintRightArea(Graphics2D g2, Rectangle2D.Float rightArea, Dimension cardDimension) {
        var player = players.get(PlayerPosition.RIGHT);
        var hand = player.getHand();

        var scale = (float) cardDimension.height / SwissCardImages.IMG_HEIGHT;
        var hand_height = (hand.size() - 1) * cardDimension.width / 2.1f + (float) cardDimension.getWidth();
        var card_y_step = hand.size() < 2 ? 0 : (hand_height - cardDimension.width) / (float) (hand.size() - 1);
        var hand_x_offset = rightArea.x + (rightArea.width - cardDimension.height) / 2;
        var hand_y_offset = rightArea.y + (rightArea.height - hand_height) / 2;
        for (int i = 0; i < hand.size(); i++) {
            var xform = new AffineTransform();
            xform.translate(hand_x_offset, hand_y_offset + card_y_step * i);
            xform.scale(scale, scale);
            xform.translate(SwissCardImages.IMG_HEIGHT / 2f, SwissCardImages.IMG_WIDTH / 2f);
            xform.rotate(toRadians(90));
            xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
            g2.drawRenderedImage(SwissCardImages.getImage(hand.get(i)), xform);
        }

        // name
        float nameX = rightArea.x + rightArea.width * 20f / 120f;
        float nameY = min(hand_y_offset - cardDimension.height / 10f, rightArea.y + rightArea.height / 4);
        g2.drawString(player.getName(), nameX, nameY);

        paintSideTrumpBadge(g2, PlayerPosition.RIGHT, player.getName(), nameX, nameY, rightArea);
        paintAnnouncements(g2, PlayerPosition.RIGHT, nameX, hand_y_offset + hand_height + g2.getFontMetrics().getHeight(),
                true, rightArea.x + rightArea.width - 4);
    }

    private float getTrumpBadgeWidth(Graphics2D g2, PlayerPosition position) {
        var metrics = g2.getFontMetrics();
        int iconSize = metrics.getAscent();
        if (position == trumpMaker && atoutColor != Card.COLOR_NONE) {
            // the crown and the suit
            return 2 * iconSize + metrics.stringWidth(" ") / 2f;
        }
        if (position == trumpChooser || position == trumpMaker || position == trumpPasser) {
            return iconSize;
        }
        return 0;
    }

    /**
     * Paints the trump badge of a player from (x, y), on the text baseline: an outlined crown while they choose the
     * trump, a crown and the suit once they chose it, an arrow to their partner if they passed.
     */
    private void paintTrumpBadge(Graphics2D g2, PlayerPosition position, float x, float y) {
        var metrics = g2.getFontMetrics();
        int iconSize = metrics.getAscent();
        float top = y - iconSize;
        if (position == trumpChooser) {
            paintCrown(g2, x, top, iconSize, false);
        } else if (position == trumpMaker) {
            paintCrown(g2, x, top, iconSize, true);
            if (atoutColor != Card.COLOR_NONE) {
                g2.drawImage(SwissCardImages.getColorImage(atoutColor),
                        round(x + iconSize + metrics.stringWidth(" ") / 2f), round(top), iconSize, iconSize, this);
            }
        } else if (position == trumpPasser) {
            paintArrowToPartner(g2, position, x, top, iconSize);
        }
    }

    private void paintCrown(Graphics2D g2, float x, float y, float size, boolean filled) {
        var crown = new java.awt.geom.Path2D.Float();
        crown.moveTo(0, 0.85);
        crown.lineTo(0, 0.25);
        crown.lineTo(0.27, 0.5);
        crown.lineTo(0.5, 0.1);
        crown.lineTo(0.73, 0.5);
        crown.lineTo(1, 0.25);
        crown.lineTo(1, 0.85);
        crown.closePath();
        // inset by the stroke width, so that the outline stays in the icon's box
        float stroke = max(1, size / 10);
        var shape = AffineTransform.getTranslateInstance(x + stroke / 2, y + stroke / 2);
        shape.scale(size - stroke, size - stroke);
        var transformed = shape.createTransformedShape(crown);
        var color = g2.getColor();
        var oldStroke = g2.getStroke();
        if (filled) {
            g2.setColor(CROWN_COLOR);
            g2.fill(transformed);
        }
        g2.setColor(TRUMP_BADGE_COLOR);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(transformed);
        g2.setStroke(oldStroke);
        g2.setColor(color);
    }

    /** An arrow pointing across the table, to the partner, to whom the player passed the trump choice. */
    private void paintArrowToPartner(Graphics2D g2, PlayerPosition position, float x, float y, float size) {
        // pointing up, in a unit square
        var arrow = new java.awt.geom.Path2D.Float();
        arrow.moveTo(0.5, 0.05);
        arrow.lineTo(0.95, 0.5);
        arrow.lineTo(0.65, 0.5);
        arrow.lineTo(0.65, 0.95);
        arrow.lineTo(0.35, 0.95);
        arrow.lineTo(0.35, 0.5);
        arrow.lineTo(0.05, 0.5);
        arrow.closePath();
        double angle = switch (position) {
            case ACROSS -> PI;
            case LEFT -> PI / 2;
            case RIGHT -> -PI / 2;
            default -> 0;
        };
        var shape = AffineTransform.getTranslateInstance(x, y);
        shape.scale(size, size);
        shape.rotate(angle, 0.5, 0.5);
        var color = g2.getColor();
        g2.setColor(TRUMP_BADGE_COLOR);
        g2.fill(shape.createTransformedShape(arrow));
        g2.setColor(color);
    }

    /** The side players' badge goes after their name if it fits in their area, else on the line above it. */
    private void paintSideTrumpBadge(Graphics2D g2, PlayerPosition position, String name, float nameX, float nameY,
                                     Rectangle2D.Float area) {
        float badgeWidth = getTrumpBadgeWidth(g2, position);
        if (badgeWidth == 0) {
            return;
        }
        var metrics = g2.getFontMetrics();
        float x = nameX + metrics.stringWidth(name + "  ");
        if (x + badgeWidth <= area.x + area.width - 4) {
            paintTrumpBadge(g2, position, x, nameY);
        } else {
            paintTrumpBadge(g2, position, nameX, nameY - metrics.getHeight());
        }
    }

    /**
     * Paints the announcements of a player from (x, y), with the suit as an icon ("Cinquante au roi ♥"). With
     * oneByLine, each announcement starts a new line, and words that would go past maxX wrap to the next line.
     */
    void paintAnnouncements(Graphics2D g2, PlayerPosition position, float x, float y, boolean oneByLine, float maxX) {
        var playerAnnouncements = announcements.get(position);
        if (playerAnnouncements == null) {
            return;
        }
        var font = g2.getFont();
        var color = g2.getColor();
        g2.setFont(font.deriveFont(Font.ITALIC));
        g2.setColor(ANNOUNCEMENT_COLOR);
        var metrics = g2.getFontMetrics();
        float lineHeight = metrics.getHeight();
        int iconSize = metrics.getAscent();
        float space = metrics.stringWidth(" ");
        float cx = x, cy = y;
        for (int i = 0; i < playerAnnouncements.size(); i++) {
            var announcement = playerAnnouncements.get(i);
            if (i > 0 && oneByLine) {
                cx = x;
                cy += lineHeight;
            } else if (i > 0) {
                cx += 3 * space;
            }
            var text = announcement.toString();
            int suit = Card.COLOR_NONE;
            if (announcement.getType() != Announcement.STOECK && announcement.getType() < Announcement.SQUARE) {
                // "au roi de cœur": the suit is drawn as an icon
                suit = announcement.getCard().getColor();
                text = text.substring(0, text.length() - (" de " + Card.COLOR_NAMES[suit]).length());
            }
            text = text.substring(0, 1).toUpperCase() + text.substring(1);
            var words = text.split(" ");
            for (int w = 0; w < words.length; w++) {
                float wordWidth = metrics.stringWidth(words[w]);
                // the suit icon stays with the last word
                float neededWidth = w == words.length - 1 && suit != Card.COLOR_NONE ? wordWidth + space + iconSize : wordWidth;
                if (w > 0) {
                    if (cx + space + neededWidth > maxX) {
                        cx = x;
                        cy += lineHeight;
                    } else {
                        cx += space;
                    }
                }
                g2.drawString(words[w], cx, cy);
                cx += wordWidth;
            }
            if (suit != Card.COLOR_NONE) {
                cx += space;
                g2.drawImage(SwissCardImages.getColorImage(suit), round(cx), round(cy - iconSize), iconSize, iconSize,
                        this);
                cx += iconSize;
            }
        }
        g2.setFont(font);
        g2.setColor(color);
    }

    void paintAnimation(Graphics2D g2, Rectangle2D.Float centerArea) {
        float scale = centerArea.width / DEFAULT_CENTER_WIDTH;
        for (var playerPosition : playedCards.keySet()) {
            paintPlayedCard(g2, playerPosition, centerArea, scale);
        }
    }

    void paintPlayedCard(Graphics2D g2, PlayerPosition position, Rectangle2D.Float centerArea, float scale) {
        double angle = cardAngles.get(position);
        var card = playedCards.get(position);
        var cardBbox = playedCardsPositions.get(position);
        switch (position) {
            case LEFT:
                angle += PI / 2;
                break;
            case RIGHT:
                angle -= PI / 2;
                break;
        }
        var xform = new AffineTransform();
        xform.translate(centerArea.x + cardBbox.x * scale, centerArea.y + cardBbox.y * scale);
        xform.scale(scale * CARD_SCALE, scale * CARD_SCALE);
        xform.translate(cardBbox.width / 2 / CARD_SCALE, cardBbox.height / 2 / CARD_SCALE);
        xform.rotate(angle);
        xform.translate(-SwissCardImages.IMG_WIDTH / 2f, -SwissCardImages.IMG_HEIGHT / 2f);
        g2.drawRenderedImage(SwissCardImages.getImage(card), xform);
        if (DEBUG) {
            var currentColor = g2.getColor();
            g2.setColor(Color.CYAN);
            g2.drawRect((int) round(centerArea.x + cardBbox.x * scale), (int) round(centerArea.y + cardBbox.y * scale), (int) round(cardBbox.width * scale), (int) round(cardBbox.height * scale));
            g2.setColor(currentColor);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        var renderingArea = getRenderingDimension();
        float scale = renderingArea.height / DEFAULT_HEIGHT;

        var cardDimension = getCardDimension(scale);

        var centerArea = getCenterArea(renderingArea);
        if (g2.getClip().intersects(centerArea)) {
            paintCenter(g2, centerArea, scale);
        }

        if (gameMode == GameMode.ANIMATION && !animationAboveHands) {
            paintAnimation(g2, centerArea);
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

        if (gameMode == GameMode.ANIMATION && animationAboveHands) {
            paintAnimation(g2, centerArea);
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
            if (players.containsKey(PlayerPosition.MYSELF) && !players.get(PlayerPosition.MYSELF).getHand().isEmpty()) {
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
        float scale = DEFAULT_HEIGHT / area.height;
        float x = DEFAULT_HEIGHT / scale;
        float panelHeight = 40 / scale;
        var size = buttonAnnounce.getPreferredSize();
        float y = 490 / scale + (panelHeight - size.height) / 2;
        buttonAnnounce.setBounds(round(area.x + x), round(area.y + y), size.width, size.height);

        statusPanel.setBounds(round(area.x), round(area.y + 490 / scale),
                round(400 / scale), round(40 / scale));
    }

    Rectangle toInt(Rectangle2D.Float rect) {
        // rounds outwards, so that the rectangle covers every pixel the area touches
        int x = (int) floor(rect.x);
        int y = (int) floor(rect.y);
        return new Rectangle(x, y, (int) ceil(rect.x + rect.width) - x, (int) ceil(rect.y + rect.height) - y);
    }

    Rectangle2D.Float getPlayerArea(PlayerPosition position) {
        var renderingArea = getRenderingDimension();
        switch (position) {
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

    Rectangle2D.Float getPlayerAreaOriginal(PlayerPosition position) {
        switch (position) {
            case MYSELF:
                return new Rectangle2D.Float(DEFAULT_CENTER_LEFT, DEFAULT_CENTER_TOP + DEFAULT_CENTER_HEIGHT, DEFAULT_CENTER_WIDTH, DEFAULT_CENTER_TOP);
            case ACROSS:
                return new Rectangle2D.Float(DEFAULT_CENTER_LEFT, 0, DEFAULT_CENTER_WIDTH, DEFAULT_CENTER_TOP);
            case LEFT:
                return new Rectangle2D.Float(0, 0, DEFAULT_CENTER_LEFT, DEFAULT_CENTER_TOP * 2 + DEFAULT_CENTER_HEIGHT);
            case RIGHT:
                return new Rectangle2D.Float(DEFAULT_CENTER_LEFT + DEFAULT_CENTER_WIDTH, 0, DEFAULT_CENTER_LEFT, DEFAULT_CENTER_TOP * 2 + DEFAULT_CENTER_HEIGHT);
        }
        return null;
    }
}
