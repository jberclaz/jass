package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.ArrayList;
import java.util.List;

public class ArtificialPlayer extends BasePlayer implements IPlayer  {
    private List<PlayerView> playerViews = new ArrayList<>();
    private List<Card> unknownCardsInGame = new ArrayList<>();
    private List<Integer> drawnCards = new ArrayList<>();

    public ArtificialPlayer(int id) {
        super(id);
    }

    @Override
    public void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() throws PlayerLeftExpection {
        throw new RuntimeException("Artificial player should not have to choose team selection method");
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) throws PlayerLeftExpection {
        drawnCards.clear();
    }

    @Override
    public int drawCard() throws PlayerLeftExpection {
        // TODO: implement
        return 0;
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) throws PlayerLeftExpection {
        drawnCards.add(card.getNumber());
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) throws PlayerLeftExpection {

    }

    @Override
    public int choosePartner() throws PlayerLeftExpection {
        throw new RuntimeException("Artificial player should not have to choose partner");
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        super.setHand(cards);
        playerViews.forEach(PlayerView::reset);
        for (int i=0; i<36; i++) {
            var newCard = new Card(i);
            if (!hand.contains(newCard)) {
                unknownCardsInGame.add(newCard);
            }
        }
    }

    @Override
    public int chooseAtout(boolean first) throws PlayerLeftExpection {
        // TODO: implement
        return 0;
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {

    }

    @Override
    public Card play() throws PlayerLeftExpection {
        return null;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {
        // TODO: select proper player
        playerViews.get(0).removeCard(card);

    }

    @Override
    public void collectPlie(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {

    }

    @Override
    public List<Announcement> getAnnouncements() throws PlayerLeftExpection {
        return null;
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) throws PlayerLeftExpection {
        // TODO: select proper player
        for (var announcement : announcements) {
            playerViews.get(0).addKnownCards(announcement.getCards());
        }
    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {

    }

    @Override
    public boolean getNewGame() throws PlayerLeftExpection {
        return false;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public void lostServerConnection() {

    }

    @Override
    public void setHandScore(int ourScore, int theirScore, Team match) throws PlayerLeftExpection {

    }
}
