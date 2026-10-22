package com.leflat.jass.common;

import com.leflat.jass.client.ConnectionInfo;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public interface IJassUi {
    void setPlayer(BasePlayer player, int relativePosition);

    void showUi(boolean enable);

    TeamSelectionMethod chooseTeamSelectionMethod();

    void prepareTeamDrawing();

    void drawCard(Lock lock, Condition condition);

    int getDrawnCardPosition();

    void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException;

    BasePlayer choosePartner(List<BasePlayer> players);

    void setPlayerHand(List<Card> hand);

    void setOtherPlayersHands(int numberOfCards);

    void removeCardFromPlayerHand(int playerPosition);

    int chooseAtout(boolean allowedToPass);

    /**
     * Before the choice, atout is COLOR_NONE and the player is the one asked to choose. After it, the player is the
     * one who was asked first: they chose the trump, or they passed and their partner chose it.
     */
    void setAtout(int atout, int positionOfPlayerToChooseAtout, boolean chosenOnFirstTurn);

    void chooseCard(Lock lock, Condition condition);

    Card getChosenCard();

    void setPlayedCard(Card card, int playerPosition);

    void prepareGame();

    void collectPlie(int playerPosition);

    void setScore(int ourScore, int opponentScore);

    boolean hasPlayerAnnounced();

    void displayStatusMessage(String message);

    void displayGameResult(Team winningTeam, boolean won);

    boolean getNewGame();

    void canceledGame(int leavingPlayerPosition);
    
    void setAnnouncementEnabled(boolean enable);

    /**
     * The valid announcements of a player, to show until the end of the hand. The stoeck is sent on its own, later.
     */
    default void setAnnouncements(int playerPosition, List<Announcement> announcements) {
    }

    void lostServerConnection();

    void displayMatch(Team team, boolean us);

    void setGameId(int gameId);

    void showMessage(String title, String message, int type);

    ConnectionInfo showConnectDialog();
}

