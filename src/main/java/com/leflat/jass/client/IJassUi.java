package com.leflat.jass.client;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.TeamSelectionMethod;
import com.leflat.jass.server.BasePlayer;

import java.util.List;
import java.util.concurrent.locks.Condition;

public interface IJassUi {
    void setPlayer(BasePlayer player, int relativePosition) throws Exception;

    void showUi(boolean enable);

    TeamSelectionMethod chooseTeamSelectionMethod();

    void prepareTeamDrawing(boolean firstAttempt);

    void drawCard(Condition condition);

    int getDrawnCardPosition();

    void setDrawnCard(BasePlayer player, int cardPosition, Card card) throws IndexOutOfBoundsException;

    void setPlayerCards(List<Card> hand);
}
