package com.leflat.jass.common;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public interface IJassUi {
    void setPlayer(BasePlayer player, int relativePosition) throws Exception;

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

    void setAtout(int atout, int positionOfPlayerToChooseAtout);

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

    void lostServerConnection();

    void displayMatch(Team team, boolean us);
}

