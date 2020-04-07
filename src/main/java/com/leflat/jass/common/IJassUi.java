package com.leflat.jass.common;

import java.util.List;

public interface IJassUi {
    void setPlayer(BasePlayer player, int relativePosition) throws Exception;

    void showUi(boolean enable);

    TeamSelectionMethod chooseTeamSelectionMethod();

    void prepareTeamDrawing(boolean firstAttempt);

    void drawCard(Thread thread);

    int getDrawnCardPosition();

    void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException;

    BasePlayer choosePartner(List<BasePlayer> players);

    void setPlayerHand(List<Card> hand);

    int chooseAtout(boolean allowedToPass);

    void setAtout(int atout, int positionOfPlayerToChooseAtout);

    void play(Plie currentPlie, Thread threadToSignal);

    Card getPlayedCard();

    void setPlayedCard(Card card, int playerPosition);

    void setOtherPlayersHands(int numberOfCards);

    void prepareGame();

    void setPlieOwner(int playerPosition);

    void setScore(int ourScore, int opponentScore);

    boolean hasPlayerAnounced();

    void displayStatusMessage(String message);

    void displayGameResult(Team winningTeam, boolean won);

    boolean getNewGame();

    void canceledGame(int leavingPlayerPosition);
}
