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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class JassFrame extends javax.swing.JFrame implements IJassUi {
    private static final String APP_TITLE = "Jass by FLAT®";

    // Autres variables
    private final IRemotePlayer myself;
    private int drawnCardPosition = -1;
    private Card playedCard = null;
    private boolean announcementPressed = false;
    private Lock lock;
    private Condition condition;

    /**
     * Creates new form ClientFrame
     */
    public JassFrame(IRemotePlayer player) {
        this.myself = player;

        initComponents();

        playerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                playerCanvas_mouseClicked(e);
            }
        });
        centerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                centerCanvas_mouseClicked(e);
            }
        });

        loadLogos();

        Locale locale = new Locale("fr", "CH");
        JOptionPane.setDefaultLocale(locale);

        pack();
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the FormEditor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        rightPanel = new CanvasBorder();
        leftPanel = new CanvasBorder();
        playerPanel = new CanvasPlayer();
        topPanel = new CanvasTop();
        centerPanel = new CanvasCenter();
        lastPliePanel = new CanvasLastPlie();
        jPanel1 = new javax.swing.JPanel();
        statusBar = new javax.swing.JLabel();
        jButtonAnounce = new javax.swing.JButton();
        jButtonConnect = new javax.swing.JButton();
        getContentPane().setLayout(new AbsoluteLayout());
        setResizable(false);
        setTitle(APP_TITLE);
        setFont(new java.awt.Font("SansSerif", Font.PLAIN, 10));
        addWindowListener(new java.awt.event.WindowAdapter() {
                              public void windowClosing(java.awt.event.WindowEvent evt) {
                                  exitForm(evt);
                              }
                          }
        );

        getContentPane().add(rightPanel, new AbsoluteConstraints(510, 0, 120, 450));
        getContentPane().add(leftPanel, new AbsoluteConstraints(0, 0, 120, 450));
        getContentPane().add(playerPanel, new AbsoluteConstraints(120, 330, 390, 120));
        getContentPane().add(topPanel, new AbsoluteConstraints(120, 0, 390, 120));
        getContentPane().add(centerPanel, new AbsoluteConstraints(120, 120, 390, 210));
        getContentPane().add(lastPliePanel, new AbsoluteConstraints(0, 450, 630, 40));

        jPanel1.setBorder(new javax.swing.border.BevelBorder(BevelBorder.LOWERED));
        jPanel1.add(statusBar);

        getContentPane().add(jPanel1, new AbsoluteConstraints(0, 490, 400, 40));

        jButtonAnounce.setText("Annonce");
        jButtonAnounce.setEnabled(false);
        jButtonAnounce.addActionListener(this::jButtonAnounceActionPerformed);

        jButtonConnect.setText("Connexion");
        jButtonConnect.addActionListener(evt -> jButtonConnectActionPerformed());

        var panelButtons = new JPanel();
        panelButtons.add(jButtonConnect, new AbsoluteConstraints(10, 10, -1, -1));
        panelButtons.add(jButtonAnounce, new AbsoluteConstraints(130, 10, -1, -1));
        getContentPane().add(panelButtons, new AbsoluteConstraints(400, 490, 230, 40));
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
                jButtonConnect.setText("Quitter");
                setGameId(gameId);
            } else {
                JOptionPane.showMessageDialog(null, "La connection a échoué.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            int choice = JOptionPane.showConfirmDialog(this, "Voulez-vous vraiment quitter le jeu?", "Déconnexion", JOptionPane.YES_NO_OPTION);
            if (choice == 0) {
                if (myself.disconnect()) {
                    disconnect();
                }
            }
        }
    }//GEN-LAST:event_jButtonConnectActionPerformed


    private void jButtonAnounceActionPerformed(java.awt.event.ActionEvent evt) {
//GEN-FIRST:event_jButtonAnounceActionPerformed
// Add your handling code here:
        announcementPressed = true;
        setAnnouncementEnabled(false);
    }//GEN-LAST:event_jButtonAnounceActionPerformed

    /**
     * Exit the Application
     */
    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        System.exit(0);
    }//GEN-LAST:event_exitForm


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private CanvasBorder rightPanel;
    private CanvasBorder leftPanel;
    private CanvasPlayer playerPanel;
    private CanvasTop topPanel;
    private CanvasCenter centerPanel;
    private CanvasLastPlie lastPliePanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel statusBar;
    private javax.swing.JButton jButtonAnounce;
    private javax.swing.JButton jButtonConnect;
    // End of variables declaration//GEN-END:variables


    void playerCanvas_mouseClicked(MouseEvent e) {
        playedCard = playerPanel.getCard(e.getX(), e.getY());
        if (playedCard == null) {
            return;
        }
        playerPanel.setMode(JassCanvas.MODE_STATIC);

        assert lock != null;
        assert condition != null;

        lock.lock();
        condition.signal();
        lock.unlock();
    }

    void centerCanvas_mouseClicked(MouseEvent e) {
        drawnCardPosition = centerPanel.getCard(e.getX(), e.getY());
        if (drawnCardPosition < 0) {
            return;
        }
        centerPanel.setMode(CanvasCenter.MODE_DRAW_TEAMS);    // on ne peut plus choisir une carte

        assert lock != null;
        assert condition != null;

        lock.lock();
        condition.signal();
        lock.unlock();
    }

    // attribue ses cartes aux joueur
    @Override
    public void setPlayerHand(List<Card> hand) {
        playerPanel.setHand(hand);
    }

    @Override
    public int chooseAtout(boolean allowedToPass) {
        Object[] options = new Object[allowedToPass ? 5 : 4];
        for (int i=0; i<4; i++) {
            options[i] = new ImageIcon(CardImages.getInstance().getColorImage(i));
        }
        if (allowedToPass) {
            options[4] = "Passer";
        }
        int choice = JOptionPane.showOptionDialog(this, "Veuillez choisir l'atout", "Choix de l'atout",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice < 0) {
            choice = allowedToPass? 4 : 0;
        }
        return choice;
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {
        lastPliePanel.setAtout(atout);
        for (int i = 0; i < 4; i++) {
            var canvas = getPlayerCanvas(i);
            canvas.setAtout(i == positionOfPlayerToChooseAtout);
        }
    }

    @Override
    public void chooseCard(Lock lock, Condition condition) {
        this.lock = lock;
        this.condition = condition;
        playerPanel.setMode(JassCanvas.MODE_PLAY);
    }

    @Override
    public Card getChosenCard() {
        return playedCard;
    }

    @Override
    public void setPlayedCard(Card card, int playerPosition) {
        centerPanel.showCard(card, playerPosition);
    }

    @Override
    public void setOtherPlayersHands(int numberOfCards) {
        var hand = new ArrayList<>(Collections.nCopies(numberOfCards, new Card(Card.BACK_NUMBER)));
        leftPanel.setHand(hand);
        topPanel.setHand(hand);
        rightPanel.setHand(hand);
    }

    @Override
    public void removeCardFromPlayerHand(int playerPosition) {
        var canvas = getPlayerCanvas(playerPosition);
        canvas.removeCard();
    }

    // prépare l'écran pour une nouvelle partie
    @Override
    public void prepareGame() {
        centerPanel.resetCards();
        centerPanel.setMode(CanvasCenter.MODE_GAME);      // mode de jeu
        lastPliePanel.hideAtout();
        setStatusBar("");
        clearLastPlie();
    }

    // ramasse la plie
    @Override
    public void collectPlie(int playerPosition) {
        if (playerPosition == 0) {
            setStatusBar("Vous avez pris la plie");
        } else {
            statusBar.setText(getPlayerCanvas(playerPosition).getName() + " a pris la plie");
        }
        lastPliePanel.setLastPlie(centerPanel.getShownCards());
        centerPanel.resetCards();
    }

    @Override
    public void setScore(int ourScore, int opponentScore) {
        lastPliePanel.setScores(ourScore, opponentScore);
    }

    @Override
    public void setAnnouncementEnabled(boolean enable) {
        if (enable) {
            announcementPressed = false;
        }
        jButtonAnounce.setEnabled(enable);
    }

    @Override
    public void lostServerConnection() {
         JOptionPane.showMessageDialog(this, "Le connexion au serveur a échoué. La partie est terminée.", "Serveur déconnecté", JOptionPane.ERROR_MESSAGE);
         disconnect();
    }

    @Override
    public boolean hasPlayerAnnounced() {
        return announcementPressed;
    }

    @Override
    public void displayStatusMessage(String message) {
        setStatusBar(message);
    }

    @Override
    public void displayGameResult(Team winningTeam, boolean won) {
        centerPanel.setMode(CanvasCenter.MODE_PASSIVE);
        playerPanel.setMode(JassCanvas.MODE_STATIC);
        String message = won ? "Vous avez gagné la partie. Félicitations!" :
                "L'équipe de " + winningTeam.getPlayer(0).getName() + " & " + winningTeam.getPlayer(1).getName() + " a gagné la partie!";
        setStatusBar("Partie terminée");
        JOptionPane.showMessageDialog(this, message, "Partie terminée", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean getNewGame() {
        int choice = JOptionPane.showConfirmDialog(this, "Voulez vous faire une nouvelle partie?", "Nouvelle partie", JOptionPane.YES_NO_OPTION);
        return choice == 0;
    }

    @Override
    public void canceledGame(int leavingPlayerPosition) {
        var canvas = getPlayerCanvas(leavingPlayerPosition);
        String message = canvas.getName() + " a quitté le jeu. La partie est interrompue.";
        setStatusBar("Partie interrompue");
        disconnect();
        JOptionPane.showMessageDialog(this, message, "Partie interrompue", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setPlayer(BasePlayer player, int relativePosition) {
        JassCanvas canvas = getPlayerCanvas(relativePosition);
        canvas.setName(player.getName());
        canvas.repaint();
    }

    @Override
    public void showUi(boolean enable) {
        setLocationRelativeTo(null);
        setVisible(enable);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        Object[] options = {"Hasard", "Manuel"};
        int choice = JOptionPane.showOptionDialog(this, "Comment voulez-vous choisir les équipes?", "Choix des équipes",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return choice == 0 ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing() {
        playerPanel.clearHand();
        leftPanel.clearHand();
        rightPanel.clearHand();
        topPanel.clearHand();
        centerPanel.resetCards();
        centerPanel.setMode(CanvasCenter.MODE_DRAW_TEAMS);
        clearLastPlie();
        lastPliePanel.hideAtout();
        setScore(0, 0);
    }

    @Override
    public void drawCard(Lock lock, Condition condition) {
        centerPanel.setMode(CanvasCenter.MODE_PICK_CARD);
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public int getDrawnCardPosition() {
        return drawnCardPosition;
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) {
        centerPanel.drawCard(cardPosition);
        var canvas = getPlayerCanvas(playerPosition);
        canvas.setHand(Collections.singletonList(card));
    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> partners) {
        Object[] options = partners.stream().map(BasePlayer::toString).toArray();
        int choice = JOptionPane.showOptionDialog(this, "Veuillez choisir votre partenaire", "Choix du partenaire",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == -1) {
            choice = 0;
        }
        return partners.get(choice);
    }

    private void setStatusBar(String text) {
        statusBar.setText(text);
        statusBar.repaint();
    }

    private JassCanvas getPlayerCanvas(int relativePosition) {
        switch (relativePosition) {
            case 0:
                return playerPanel;
            case 1:
                return rightPanel;
            case 2:
                return topPanel;
            case 3:
                return leftPanel;
            default:
                throw new IndexOutOfBoundsException("Unknown canvas " + relativePosition);
        }
    }

    private void loadLogos() {
        var logoNames = new String[]{"logos/logo_128.png", "logos/logo_64.png", "logos/logo_48.png", "logos/logo_32.png", "logos/logo_16.png"};
        List<Image> images = new ArrayList<>();

        for (var logoName : logoNames) {
            var imagePath = getClass().getClassLoader().getResource(logoName);
            if (imagePath == null) {
                System.err.println("Unable to locate logo " + logoName);
                continue;
            }
            try {
                images.add(ImageIO.read(imagePath));
            } catch (IOException e) {
                System.err.println("Unable to open logo " + imagePath);
                e.printStackTrace();
            }
        }

        setIconImages(images);
    }

    private void setGameId(int gameId) {
        int lowId = gameId % 1000;
        int highId = gameId / 1000;
        String title = gameId >= 0 ? APP_TITLE + " - Jeu " + highId + " " + lowId : APP_TITLE;
        setTitle(title);
    }

    private void clearLastPlie() {
        lastPliePanel.setLastPlie(Collections.emptyList());
    }

    private void disconnect() {
        for (int i = 0; i < 4; i++) {
            var canvas = getPlayerCanvas(i);
            canvas.clearHand();
            canvas.setName("");
            canvas.setAtout(false);
            canvas.setMode(JassCanvas.MODE_STATIC);
        }
        centerPanel.resetCards();
        centerPanel.setMode(CanvasCenter.MODE_PASSIVE);
        clearLastPlie();
        lastPliePanel.hideAtout();
        setScore(0, 0);
        setGameId(-1);

        statusBar.setText("");
        jButtonConnect.setText("Connexion");
    }
}
