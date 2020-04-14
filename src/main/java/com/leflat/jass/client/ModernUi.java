package com.leflat.jass.client;

import com.leflat.jass.common.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class ModernUi extends JFrame implements IJassUi, MouseListener {
    private static final String APP_TITLE = "Jass by FLAT®";

    private final IRemotePlayer myself;
    private ModernGamePanel gamePanel;

    public ModernUi(IRemotePlayer player) {
        this.myself = player;
        initComponents();
        loadLogos();
    }

    private void initComponents() {
        gamePanel = new ModernGamePanel();
        getContentPane().add(gamePanel);
        setSize(800, 600);
        setResizable(true);
        setTitle(APP_TITLE);
        setFont(new java.awt.Font("SansSerif", Font.PLAIN, 10));
    }

    @Override
    public void setPlayer(BasePlayer player, int relativePosition) {
        gamePanel.setPlayer(intToPlayerPosition(relativePosition), player);
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
        gamePanel.setMode(ModernGamePanel.GameMode.TEAM_DRAWING);
    }

    @Override
    public void drawCard(Lock lock, Condition condition) {

    }

    @Override
    public int getDrawnCardPosition() {
        return 0;
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException {
        gamePanel.drawCard(cardPosition, card, intToPlayerPosition(playerPosition));
    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> players) {
        return null;
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
        return 0;
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {

    }

    @Override
    public void chooseCard(Lock lock, Condition condition) {

    }

    @Override
    public Card getChosenCard() {
        return null;
    }

    @Override
    public void setPlayedCard(Card card, int playerPosition) {
        gamePanel.setPlayedCard(intToPlayerPosition(playerPosition), card);
    }

    @Override
    public void prepareGame() {
        gamePanel.setMode(ModernGamePanel.GameMode.GAME);
    }

    @Override
    public void collectPlie(int playerPosition) {

    }

    @Override
    public void setScore(int ourScore, int opponentScore) {

    }

    @Override
    public boolean hasPlayerAnnounced() {
        return false;
    }

    @Override
    public void displayStatusMessage(String message) {

    }

    @Override
    public void displayGameResult(Team winningTeam, boolean won) {

    }

    @Override
    public boolean getNewGame() {
        return false;
    }

    @Override
    public void canceledGame(int leavingPlayerPosition) {

    }

    @Override
    public void setAnnouncementEnabled(boolean enable) {

    }

    @Override
    public void lostServerConnection() {

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
}
