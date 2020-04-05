package com.leflat.jass.common;

import com.leflat.jass.server.BasePlayer;
import com.leflat.jass.server.Team;

import java.util.List;

public interface IPlayer {
    void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection;

    TeamSelectionMethod chooseTeamSelectionMethod() throws PlayerLeftExpection;

    void prepareTeamDrawing(boolean firstAttempt) throws PlayerLeftExpection;

    int drawCard() throws PlayerLeftExpection;

    void setCard(BasePlayer player, int cardPosition, Card card) throws PlayerLeftExpection;

    void setPlayersOrder(List<Integer> playerIds) throws PlayerLeftExpection;

    int choosePartner() throws PlayerLeftExpection;

    void setHand(List<Card> cards) throws PlayerLeftExpection;

    int chooseAtout(boolean first) throws PlayerLeftExpection;

    void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection;

    void play(int currentColor, int highestRank, boolean cut) throws PlayerLeftExpection;

    void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection;

    void setPlieOwner(BasePlayer player) throws PlayerLeftExpection;

    void setScores(int score, int opponentScore) throws PlayerLeftExpection;

    List<Anouncement> getAnoucement() throws PlayerLeftExpection;

    void setAnouncement(BasePlayer player, List<Anouncement> anouncements) throws PlayerLeftExpection;

    void setGameResult(Team winningTeam) throws PlayerLeftExpection;

    boolean getNewGame() throws PlayerLeftExpection;

    void playerLeft(BasePlayer player) throws PlayerLeftExpection;
}
