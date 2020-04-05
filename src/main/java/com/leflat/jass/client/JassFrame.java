/*
 * ClientFrame.java
 *
 * Created on 18. avril 2000, 16:10
 */

/*
 * @author Jérôme Berclaz
 * @version 2.0
 */

package com.leflat.jass.client;

import com.leflat.jass.common.*;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JassFrame extends javax.swing.JFrame implements IJassUi {
    private static final String APP_TITLE = "Jass by FLAT®";

    // Variables visuelles
    private CanvasBorder leftCanvas;
    private CanvasBorder rightCanvas;
    private CanvasPlayer playerCanvas;
    private CanvasTop topCanvas;
    private CanvasCenter centerCanvas;
    private CanvasLastPlie lastPlieCanvas;

    // Autres variables
    private IRemotePlayer myself;
    private Thread threadToSignal = null;
    private int drawnCardPosition = -1;
    private Plie currentPlie;
    private Card playedCard = null;
    private int atoutColor;
    private boolean anoucementPressed = false;


    /**
     * Creates new form ClientFrame
     */
    public JassFrame(IRemotePlayer player) {
        this.myself = player;

        initComponents();

        leftCanvas = new CanvasBorder();
        rightCanvas = new CanvasBorder();
        playerCanvas = new CanvasPlayer();
        topCanvas = new CanvasTop();
        centerCanvas = new CanvasCenter();
        lastPlieCanvas = new CanvasLastPlie();
        leftPanel.add(leftCanvas);
        rightPanel.add(rightCanvas);
        topPanel.add(topCanvas);
        playerPanel.add(playerCanvas);
        centerPanel.add(centerCanvas);
        lastPliePanel.add(lastPlieCanvas);
        playerCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                playerCanvas_mouseClicked(e);
            }
        });
        centerCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                centerCanvas_mouseClicked(e);
            }
        });

        pack();
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the FormEditor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        rightPanel = new javax.swing.JPanel();
        leftPanel = new javax.swing.JPanel();
        playerPanel = new javax.swing.JPanel();
        topPanel = new javax.swing.JPanel();
        centerPanel = new javax.swing.JPanel();
        lastPliePanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        statusBar = new javax.swing.JLabel();
        infoBar = new javax.swing.JLabel();
        jButtonAnounce = new javax.swing.JButton();
        jButtonConnect = new javax.swing.JButton();
        getContentPane().setLayout(new AbsoluteLayout());
        setSize(660, 565);
        setResizable(false);
        setTitle(APP_TITLE);
        setFont(new java.awt.Font("SansSerif", 0, 10));
        addWindowListener(new java.awt.event.WindowAdapter() {
                              public void windowClosing(java.awt.event.WindowEvent evt) {
                                  exitForm(evt);
                              }
                          }
        );

        rightPanel.setLayout(new java.awt.GridLayout(1, 1));
        rightPanel.setName("rightPanel");


        getContentPane().add(rightPanel, new AbsoluteConstraints(510, 0, 120,
                450));

        leftPanel.setLayout(new java.awt.GridLayout(1, 1));
        leftPanel.setName("leftPanel");


        getContentPane().add(leftPanel, new AbsoluteConstraints(0, 0, 120,
                450));

        playerPanel.setLayout(new java.awt.GridLayout(1, 1));


        getContentPane().add(playerPanel, new AbsoluteConstraints(120, 330,
                390, 120));

        topPanel.setLayout(new java.awt.GridLayout(1, 1));


        getContentPane().add(topPanel, new AbsoluteConstraints(120, 0, 390,
                120));

        centerPanel.setLayout(new java.awt.GridLayout(1, 1));
        centerPanel.setBackground(new java.awt.Color(51, 102, 0));


        getContentPane().add(centerPanel, new AbsoluteConstraints(120, 120,
                390, 210));

        lastPliePanel.setLayout(new java.awt.GridLayout(1, 1));


        getContentPane().add(lastPliePanel, new AbsoluteConstraints(0, 450,
                630, 40));

        jPanel1.setBorder(new javax.swing.border.BevelBorder(1));


        jPanel1.add(statusBar);
        jPanel1.add(infoBar);


        getContentPane().add(jPanel1, new AbsoluteConstraints(0, 490, 400, 40));

        jButtonAnounce.setAlignmentY(0.4F);
        jButtonAnounce.setText("Annoncer");
        jButtonAnounce.setEnabled(false);
        jButtonAnounce.addActionListener(new java.awt.event.ActionListener() {
                                             public void actionPerformed(java.awt.event.ActionEvent evt) {
                                                 jButtonAnounceActionPerformed(evt);
                                             }
                                         }
        );


        getContentPane().add(jButtonAnounce, new AbsoluteConstraints(530, 500,
                -1, -1));

        jButtonConnect.setText("Connexion");
        jButtonConnect.addActionListener(new java.awt.event.ActionListener() {
                                             public void actionPerformed(java.awt.event.ActionEvent evt) {
                                                 jButtonConnectActionPerformed();
                                             }
                                         }
        );


        getContentPane().add(jButtonConnect, new AbsoluteConstraints(410, 500,
                -1, -1));

    }//GEN-END:initComponents

    private void jButtonConnectActionPerformed() {
        //GEN-FIRST:event_jButtonConnectActionPerformed
        if (!myself.isConnected()) {
            var dc = new DialogConnect(this, true);
            dc.setLocationRelativeTo(this);
            dc.setVisible(true);
            if (!dc.ok) {
                return;
            }
            int gameId = myself.connect(dc.name, dc.host, dc.gameId);
            if (gameId >= 0) {
                jButtonConnect.setText("Déconnexion");
                setGameId(gameId);
            } else {
                // TODO: error message
            }
        } else {
            // TODO: DISPLAY A CONFIRMATION MSG
            if (myself.disconnect()) {
                disconnect();
            }
        }
    }//GEN-LAST:event_jButtonConnectActionPerformed


    private void jButtonAnounceActionPerformed(java.awt.event.ActionEvent evt) {
//GEN-FIRST:event_jButtonAnounceActionPerformed
// Add your handling code here:
        anoucementPressed = true;
        jButtonAnounce.setEnabled(false);
    }//GEN-LAST:event_jButtonAnounceActionPerformed

    /**
     * Exit the Application
     */
    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        System.exit(0);
    }//GEN-LAST:event_exitForm


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel rightPanel;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel playerPanel;
    private javax.swing.JPanel topPanel;
    private javax.swing.JPanel centerPanel;
    private javax.swing.JPanel lastPliePanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel statusBar;
    private javax.swing.JLabel infoBar;
    private javax.swing.JButton jButtonAnounce;
    private javax.swing.JButton jButtonConnect;
    // End of variables declaration//GEN-END:variables


    void playerCanvas_mouseClicked(MouseEvent e) {
        playedCard = playerCanvas.getCard(e.getX(), e.getY());
        if (playedCard == null) {
            return;
        }

        int decision = Rules.canPlay(playedCard, currentPlie, playerCanvas.getHand(), atoutColor);
        switch (decision) {
            case Rules.RULES_MUST_FOLLOW:
                playedCard = null;
                statusBar.setText("Il faut suivre!");
                return;
            case Rules.RULES_CANNOT_UNDERCUT:
                playedCard = null;
                statusBar.setText("Vous ne pouvez pas sous-couper!");
                return;
        }

        jButtonAnounce.setEnabled(false);
        setStatusBar("");
        centerCanvas.showCard(playedCard, 0);
        playerCanvas.setMode(JassCanvas.MODE_STATIC);
        playerCanvas.removeCard(playedCard);
        assert threadToSignal != null;
        synchronized (threadToSignal) {
            threadToSignal.notify();
        }
    }

    void centerCanvas_mouseClicked(MouseEvent e) {
        System.out.println("Center click: " + e.getX() + " " + e.getY());
        drawnCardPosition = centerCanvas.getCard(e.getX(), e.getY());
        if (drawnCardPosition < 0) {
            return;
        }
        centerCanvas.setMode(CanvasCenter.MODE_DRAW_TEAMS);    // on ne peut plus choisir une carte
        setStatusBar("");         // remove "veuillez tirer..."
        assert threadToSignal != null;
        synchronized (threadToSignal) {
            threadToSignal.notify();
        }
    }

    //Fait un repaint des différents canvas
    public void repaint(int nbr) {
        if ((nbr & 1) == 1)
            playerCanvas.repaint();
        if ((nbr & 2) == 2)
            leftCanvas.repaint();
        if ((nbr & 4) == 4)
            topCanvas.repaint();
        if ((nbr & 8) == 8)
            rightCanvas.repaint();
        if ((nbr & 16) == 16)
            centerCanvas.repaint();
    }

    public void disconnect() {
        playerCanvas.clearHand();
        playerCanvas.setName("");
        playerCanvas.setAtout(false);
        leftCanvas.clearHand();
        leftCanvas.setMode(1);
        leftCanvas.setName("");
        leftCanvas.setAtout(false);
        rightCanvas.clearHand();
        rightCanvas.setMode(1);
        rightCanvas.setName("");
        rightCanvas.setAtout(false);
        topCanvas.clearHand();
        topCanvas.setMode(1);
        topCanvas.setName("");
        topCanvas.setAtout(false);
        centerCanvas.setMode(0);
        centerCanvas.repaint();
        removeLastPlie();
        lastPlieCanvas.setAtout(4);
        setScore(0, 0);
        repaint(31);

        statusBar.setText("Déconnexion");
        jButtonConnect.setText("Connexion");
    }

    // attribue ses cartes aux joueur
    public void setPlayerHand(List<Card> hand) {
        playerCanvas.setHand(hand);
    }

    @Override
    public int chooseAtout(boolean allowedToPass) {
        DialogAtout da = new DialogAtout(this, true, allowedToPass);
        da.setLocationRelativeTo(this);
        da.setVisible(true);
        return da.getSelectedColor();
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {
        atoutColor = atout;
        lastPlieCanvas.setAtout(atout);
        for (int i = 0; i < 4; i++) {
            var canvas = getPlayerCanvas(i);
            canvas.setAtout(i == positionOfPlayerToChooseAtout);
        }
    }

    @Override
    public void play(Plie currentPlie, Thread threadToSignal) {
        this.currentPlie = currentPlie;
        this.threadToSignal = threadToSignal;
        setStatusBar("A vous de jouer ...");
        anoucementPressed = false;
        setAnouncementEnabled(true);
        playerCanvas.setMode(JassCanvas.MODE_PLAY);
        centerCanvas.resetCards();
        centerCanvas.setMode(CanvasCenter.MODE_GAME);
    }

    @Override
    public Card getPlayedCard() {
        return playedCard;
    }

    @Override
    public void setPlayedCard(Card card, int playerPosition) {
        var canvas = getPlayerCanvas(playerPosition);
        canvas.removeCard();
        centerCanvas.showCard(card, playerPosition);
    }

    @Override
    public void setOtherPlayersHands(int numberOfCards) {
        var hand = new ArrayList<Card>();
        for (int i = 0; i < numberOfCards; i++) {
            hand.add(new Card(Card.BACK_NUMBER));
        }
        leftCanvas.setHand(hand);
        topCanvas.setHand(hand);
        rightCanvas.setHand(hand);
    }


    // prépare l'écran pour une nouvelle partie
    @Override
    public void prepareGame() {
        centerCanvas.resetCards();
        centerCanvas.setMode(CanvasCenter.MODE_GAME);      // mode de jeu
        lastPlieCanvas.hideAtout();
        setStatusBar("");
        removeLastPlie();
    }

    // affiche une carte jouée
    void showPlayedCard(int player, int number) {
        /*
        int diff = player - app.getPlayerId();
        if (diff < 0)
            diff += 4;
        centerCanvas.showCard(new Card(number), diff);
        switch (diff) {
            case 1:
                rightCanvas.removeCard();
                rightCanvas.repaint();
                break;
            case 2:
                topCanvas.removeCard();
                topCanvas.repaint();
                break;
            case 3:
                leftCanvas.removeCard();
                leftCanvas.repaint();
        }
        centerCanvas.repaint();
        // TODO: fix
        //statusBar.setText(app.players[player].getFirstName() + " a joué...");

         */
    }

    // ramasse la plie
    @Override
    public void setPlieOwner(int playerPosition) {
        if (playerPosition == 0) {
            setStatusBar("Vous avez pris la plie");
        } else {
            statusBar.setText(getPlayerCanvas(playerPosition).getName() + " a pris la plie");
        }
        lastPlieCanvas.setLastPlie(centerCanvas.getShownCards());
        centerCanvas.resetCards();
    }

    void removeLastPlie() {
        lastPlieCanvas.setLastPlie(Collections.emptyList());
        lastPlieCanvas.repaint();
    }

    public void setScore(int ourScore, int opponentScore) {
        lastPlieCanvas.setScores(ourScore, opponentScore);
    }

    @Override
    public boolean hasPlayerAnounced() {
        return anoucementPressed;
    }

    @Override
    public void displayStatusMessage(String message) {
        setStatusBar(message);
    }

    void setStatusBar(String text) {
        statusBar.setText(text);
    }

    void setAnouncementEnabled(boolean b) {
        jButtonAnounce.setEnabled(b);
    }

    void setGameId(int gameId) {
        String title = APP_TITLE + " - Jeu " + gameId;
        setTitle(title);
    }

    @Override
    public void setPlayer(BasePlayer player, int relativePosition) {
        JassCanvas canvas = getPlayerCanvas(relativePosition);
        canvas.setName(player.getName());
        canvas.repaint();
    }

    @Override
    public void showUi(boolean enable) {
        setVisible(enable);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        DialogTeamChoice dtc = new DialogTeamChoice(this, true);
        dtc.setLocationRelativeTo(this);
        dtc.setVisible(true);
        return dtc.hasard ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {
        System.out.println("frame: prepare team drawing");
        playerCanvas.clearHand();
        leftCanvas.clearHand();
        rightCanvas.clearHand();
        topCanvas.clearHand();
        centerCanvas.resetCards();
        centerCanvas.setMode(CanvasCenter.MODE_DRAW_TEAMS);
        removeLastPlie();
        lastPlieCanvas.setAtout(4);
        setScore(0, 0);
        repaint(31);
    }

    @Override
    public void drawCard(Thread thread) {
        centerCanvas.setMode(CanvasCenter.MODE_PICK_CARD);
        setStatusBar("Veuillez choisir une carte");
        this.threadToSignal = thread;
    }

    @Override
    public int getDrawnCardPosition() {
        return drawnCardPosition;
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) {
        centerCanvas.drawCard(cardPosition);
        var canvas = getPlayerCanvas(playerPosition);
        canvas.setHand(Collections.singletonList(card));
    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> partners) {
        DialogPartnerChoice dpc = new DialogPartnerChoice(this, true);
        dpc.setLocationRelativeTo(this);
        partners.forEach(dpc::addPlayer);
        dpc.setVisible(true);
        return dpc.getSelectedPlayer();
    }

    private JassCanvas getPlayerCanvas(int relativePosition) {
        switch (relativePosition) {
            case 0:
                return playerCanvas;
            case 1:
                return rightCanvas;
            case 2:
                return topCanvas;
            case 3:
                return leftCanvas;
            default:
                throw new IndexOutOfBoundsException("Unknown canvas " + relativePosition);
        }
    }
}
