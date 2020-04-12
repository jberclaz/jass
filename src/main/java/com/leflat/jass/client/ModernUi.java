package com.leflat.jass.client;

import com.leflat.jass.common.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class ModernUi extends JFrame implements IJassUi {
    private static final String APP_TITLE = "Jass by FLAT®";

    private final IRemotePlayer myself;
    private ModernGamePanel gamePanel;

    public ModernUi(IRemotePlayer player) {
        this.myself = player;
        initComponents();
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
    public void setPlayer(BasePlayer player, int relativePosition) throws Exception {

    }

    @Override
    public void showUi(boolean enable) {
        setLocationRelativeTo(null);
        setVisible(enable);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        return null;
    }

    @Override
    public void prepareTeamDrawing() {

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

    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> players) {
        return null;
    }

    @Override
    public void setPlayerHand(List<Card> hand) {

    }

    @Override
    public void setOtherPlayersHands(int numberOfCards) {

    }

    @Override
    public void removeCardFromPlayerHand(int playerPosition) {

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

    }

    @Override
    public void prepareGame() {

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
}
