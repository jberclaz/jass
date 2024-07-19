package com.leflat.jass.client;

import com.leflat.jass.common.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModernUi extends JFrame implements IJassUi, MouseListener {
    private static final String APP_TITLE = "Jass by FLAT®";
    private static final Logger LOGGER = Logger.getLogger(OriginalUi.class.getName());

    private final IConnectable myself;
    private ModernGamePanel gamePanel;
    private Lock lock;
    private Condition condition;
    private String myName;
    private String serverHost;
    private int drawnCardNumber;
    private Card playedCard;


    public ModernUi(IConnectable player) {
        this.myself = player;
        initComponents();
        loadLogos();
    }

    private void initComponents() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            LOGGER.log(Level.WARNING, "Unable to set look and feel", e);
        }
        gamePanel = new ModernGamePanel();
        setContentPane(gamePanel);
        setTitle(APP_TITLE);
        setFont(new java.awt.Font("SansSerif", Font.PLAIN, 10));
        Locale locale = new Locale("fr", "CH");
        JOptionPane.setDefaultLocale(locale);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitUi();
            }
        });

        gamePanel.addMouseListener(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    @Override
    public void setPlayer(BasePlayer player, int relativePosition) {
        gamePanel.setPlayer(intToPlayerPosition(relativePosition), player);
    }

    @Override
    public void showUi(boolean enable) {
        setLocationRelativeTo(null);
        setVisible(enable);

        var insets = getInsets();
        setSize(630, 530 + insets.top);
        setMinimumSize(new Dimension(630, 530 + insets.top));
        setResizable(true);

        connectDialog();
    }

    private void connectDialog() {
        int returnCode;
        do {
            DialogConnection dc;
            if (myName != null && serverHost != null) {
                dc = new DialogConnection(this, myName, serverHost);
            } else {
                dc = new DialogConnection(this);
            }
            dc.pack();
            dc.setLocationRelativeTo(this);
            dc.setVisible(true);
            if (!dc.ok) {
                this.setVisible(false);
                this.dispose();
                return;
            }
            myName = dc.name;
            serverHost = dc.host;
            returnCode = myself.connect(dc.name, dc.host, dc.gameId);
            if (returnCode >= 0) {
                setGameId(returnCode);
                if (dc.gameId < 0 && dc.aiPlayers > 0) {
                    for (int i=0; i<dc.aiPlayers; ++i) {
                        var aiName = String.format("iBerte %d", i+1);
                        var aiPlayer = new RemoteArtificialPlayer(new ClientNetworkFactory());
                        aiPlayer.connect(aiName, dc.host, returnCode);
                    }
                }
            } else {
                switch (returnCode) {
                    case ConnectionError.SERVER_UNREACHABLE:
                        JOptionPane.showMessageDialog(this, "La connection a échoué.", "Erreur", JOptionPane.ERROR_MESSAGE);
                        break;
                    case ConnectionError.GAME_FULL:
                        JOptionPane.showMessageDialog(this, "Ce jeu est déja complet.", "Erreur", JOptionPane.ERROR_MESSAGE);
                        break;
                    case ConnectionError.UNKNOWN_GAME:
                        JOptionPane.showMessageDialog(this, "Le jeu " + dc.gameId + " n'existe pas.", "Erreur", JOptionPane.ERROR_MESSAGE);
                        break;
                }
            }
        } while (returnCode < 0);
    }

    void exitUi() {
        if (myself.isConnected()) {
            int choice = JOptionPane.showConfirmDialog(this, "Voulez-vous vraiment quitter le jeu?", "Déconnexion", JOptionPane.YES_NO_OPTION);
            if (choice != 0) {
                return;
            }
            myself.disconnect();
        }
        setVisible(false);
        dispose();
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
        gamePanel.clearCards();
        gamePanel.setMode(ModernGamePanel.GameMode.TEAM_DRAWING);
    }

    @Override
    public void drawCard(Lock lock, Condition condition) {
        gamePanel.setInteractive(true);
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public int getDrawnCardPosition() {
        return drawnCardNumber;
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException {
        gamePanel.drawCard(cardPosition, card, intToPlayerPosition(playerPosition));
    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> players) {
        Object[] options = players.stream().map(BasePlayer::toString).toArray();
        int choice = JOptionPane.showOptionDialog(this, "Veuillez choisir votre partenaire", "Choix du partenaire",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == -1) {
            choice = 0;
        }
        return players.get(choice);
    }

    @Override
    public void setPlayerHand(List<Card> hand) {
        gamePanel.setHand(PlayerPosition.MYSELF, hand);
    }

    @Override
    public void setOtherPlayersHands(int numberOfCards) {
        gamePanel.setHand(PlayerPosition.LEFT, Collections.nCopies(numberOfCards, Card.getBack()));
        gamePanel.setHand(PlayerPosition.RIGHT, Collections.nCopies(numberOfCards, Card.getBack()));
        gamePanel.setHand(PlayerPosition.ACROSS, Collections.nCopies(numberOfCards, Card.getBack()));
    }

    @Override
    public void removeCardFromPlayerHand(int playerPosition) {
        gamePanel.removeCard(intToPlayerPosition(playerPosition));
    }

    @Override
    public int chooseAtout(boolean allowedToPass) {
        if (allowedToPass) {
            gamePanel.setAtoutChooser(PlayerPosition.MYSELF);
        }
        Object[] options = new Object[allowedToPass ? 5 : 4];
        for (int i = 0; i < 4; i++) {
            options[i] = new ImageIcon(CardImages.getColorImage(i));
        }
        if (allowedToPass) {
            options[4] = "Passer";
        }
        int choice = JOptionPane.showOptionDialog(this, "Veuillez choisir l'atout", "Choix de l'atout",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice < 0) {
            choice = allowedToPass ? Card.COLOR_NONE : Card.COLOR_SPADE;
        }
        return choice;
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {
        gamePanel.setAtoutColor(atout);
        gamePanel.setAtoutChooser(intToPlayerPosition(positionOfPlayerToChooseAtout));
    }

    @Override
    public void chooseCard(Lock lock, Condition condition) {
        this.lock = lock;
        this.condition = condition;
        gamePanel.setInteractive(true);
    }

    @Override
    public Card getChosenCard() {
        return playedCard;
    }

    @Override
    public void setPlayedCard(Card card, int playerPosition) {
        gamePanel.setPlayedCard(intToPlayerPosition(playerPosition), card);
    }

    @Override
    public void prepareGame() {
        gamePanel.clearCards();
        gamePanel.setMode(ModernGamePanel.GameMode.GAME);
    }

    @Override
    public void collectPlie(int playerPosition) {
        gamePanel.collectCards(intToPlayerPosition(playerPosition));
    }

    @Override
    public void setScore(int ourScore, int opponentScore) {
        gamePanel.setScores(ourScore, opponentScore);
    }

    @Override
    public boolean hasPlayerAnnounced() {
        return gamePanel.announcementPressed();
    }

    @Override
    public void displayStatusMessage(String message) {
        gamePanel.displayStatusMessage(message);
    }

    @Override
    public void displayGameResult(Team winningTeam, boolean won) {
        String message = won ? "Vous avez gagné la partie. Félicitations!" :
                "L'équipe de " + winningTeam.getPlayer(0).getName() + " & " + winningTeam.getPlayer(1).getName() + " a gagné la partie!";
        JOptionPane.showMessageDialog(this, message, "Partie terminée", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean getNewGame() {
        int choice = JOptionPane.showConfirmDialog(this, "Voulez vous faire une nouvelle partie?", "Nouvelle partie", JOptionPane.YES_NO_OPTION);
        return choice == 0;
    }

    @Override
    public void canceledGame(int leavingPlayerPosition) {
        String message = "La partie est interrompue.";
        JOptionPane.showMessageDialog(this, message, "Partie interrompue", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setAnnouncementEnabled(boolean enable) {
        gamePanel.setAnnouncementEnabled(enable);
    }

    @Override
    public void lostServerConnection() {
        JOptionPane.showMessageDialog(this, "Le connexion au serveur a échoué. La partie est terminée.", "Serveur déconnecté", JOptionPane.ERROR_MESSAGE);
        disconnect();
    }

    @Override
    public void displayMatch(Team team, boolean us) {
        String message = us ? "Vous avez fait match!" :
                "L'équipe de " + team.getPlayer(0).getName() + " & " + team.getPlayer(1).getName() + " a fait match.";
        JOptionPane.showMessageDialog(this, message, "Match", JOptionPane.INFORMATION_MESSAGE);
    }

    private PlayerPosition intToPlayerPosition(int position) {
        switch (position) {
            case 0:
                return PlayerPosition.MYSELF;
            case 1:
                return PlayerPosition.RIGHT;
            case 2:
                return PlayerPosition.ACROSS;
            case 3:
                return PlayerPosition.LEFT;
        }
        throw new IndexOutOfBoundsException("Unknown position " + position);
    }

    private void loadLogos() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        List<Image> images = new ArrayList<>();

        var imagePath = getClass().getClassLoader().getResource("logos/logo_128.png");
        images.add(tk.getImage(imagePath));
        imagePath = getClass().getClassLoader().getResource("logos/logo_64.png");
        images.add(tk.getImage(imagePath));
        imagePath = getClass().getClassLoader().getResource("logos/logo_48.png");
        images.add(tk.getImage(imagePath));
        imagePath = getClass().getClassLoader().getResource("logos/logo_32.png");
        images.add(tk.getImage(imagePath));
        imagePath = getClass().getClassLoader().getResource("logos/logo_16.png");
        images.add(tk.getImage(imagePath));

        setIconImages(images);
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        var card = gamePanel.getCard(mouseEvent.getX(), mouseEvent.getY());
        if (card == null) {
            return;
        }
        gamePanel.setInteractive(false);
        if (gamePanel.getMode() == ModernGamePanel.GameMode.TEAM_DRAWING) {
            drawnCardNumber = card.getNumber();
        } else {
            playedCard = card;
        }

        assert lock != null;
        assert condition != null;

        lock.lock();
        condition.signal();
        lock.unlock();
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseEntered(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {

    }

    private void setGameId(int gameId) {
        int lowId = gameId % 1000;
        int highId = gameId / 1000;
        String title = gameId >= 0 ? APP_TITLE + String.format(" - Jeu %03d %03d", highId, lowId) : APP_TITLE;
        setTitle(title);
    }

    private void disconnect() {
        for (int i = 0; i < 4; i++) {
            gamePanel.clearPlayer(intToPlayerPosition(i));
        }
        gamePanel.clearCards();
        gamePanel.hideAtout();
        gamePanel.setMode(ModernGamePanel.GameMode.IDLE);
        setScore(0, 0);
        setGameId(-1);

        connectDialog();
    }
}
