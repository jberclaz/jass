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

    void setPlayerCards(List<Card> hand);

    int chooseAtout(boolean allowedToPass);
}
