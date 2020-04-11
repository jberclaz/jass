package com.leflat.jass.common;

import com.leflat.jass.server.PlayerLeftExpection;

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

    Card play() throws PlayerLeftExpection;

    void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection;

    void collectPlie(BasePlayer player) throws PlayerLeftExpection;

    void setScores(int score, int opponentScore) throws PlayerLeftExpection;

    List<Announcement> getAnnouncements() throws PlayerLeftExpection;

    void setAnnouncements(BasePlayer player, List<Announcement> announcements) throws PlayerLeftExpection;

    void setGameResult(Team winningTeam) throws PlayerLeftExpection;

    boolean getNewGame() throws PlayerLeftExpection;

    void playerLeft(BasePlayer player) throws PlayerLeftExpection;

    void lostServerConnection();
}
