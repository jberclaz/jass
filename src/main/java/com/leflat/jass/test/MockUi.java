package com.leflat.jass.test;

import com.leflat.jass.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class MockUi implements IJassUi {
    private final Random rand = new Random();
    private List<Card> hand;
    private final IRemotePlayer player;
    private float delaySeconds = 0;
    private final List<Integer> drawnCards = new ArrayList<>();
    private int nbrGames;
    private int playedGames = 0;

    public MockUi(IRemotePlayer player, float delaySeconds, int nbrGames) {
        this.player = player;
        this.delaySeconds = delaySeconds;
        this.nbrGames = nbrGames;
    }

    @Override
    public void setPlayer(BasePlayer player, int relativePosition) {

    }

    @Override
    public void showUi(boolean enable) {
        player.connect("GC", "localhost", 1234);
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        waitSec(delaySeconds * 2);
        return rand.nextBoolean() ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing() {
        drawnCards.clear();
    }

    @Override
    public void drawCard(Lock lock, Condition condition) {
        Runnable runnable = () -> {
            waitSec(delaySeconds);
            lock.lock();
            condition.signal();
            lock.unlock();
        };

        Thread thread = new Thread(runnable, "mock-ui-thread-draw");
        thread.start();
    }

    @Override
    public int getDrawnCardPosition() {
        int randomCard;
        do {
            randomCard = rand.nextInt(Card.DECK_SIZE);
        } while (drawnCards.contains(randomCard));
        return randomCard;
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException {
        drawnCards.add(cardPosition);
    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> players) {
        waitSec(2 * delaySeconds);
        return players.get(rand.nextInt(players.size()));
    }

    @Override
    public void setPlayerHand(List<Card> hand) {
        this.hand = hand;
    }

    @Override
    public void setOtherPlayersHands(int numberOfCards) {

    }

    @Override
    public void removeCardFromPlayerHand(int playerPosition) {
    }

    @Override
    public int chooseAtout(boolean allowedToPass) {
        waitSec(delaySeconds * 5);
        int[] colors = {0, 0, 0, 0};
        for (var c : hand) {
            colors[c.getColor()]++;
        }
        int nbrColors = 0;
        int bestColor = -1;
        int bestColorCount = 0;
        for (int colorIdx = 0; colorIdx < 4; colorIdx++) {
            if (colors[colorIdx] > 0) {
                nbrColors++;
                if (colors[colorIdx] > bestColorCount) {
                    bestColor = colorIdx;
                    bestColorCount = colors[colorIdx];
                }
            }
        }
        if (allowedToPass && nbrColors == 4 && bestColorCount < 4) {
            return Card.COLOR_NONE;
        }
        return bestColor;
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {

    }

    @Override
    public void chooseCard(Lock lock, Condition condition) {
        Runnable runnable = () -> {
            waitSec(delaySeconds);
            lock.lock();
            condition.signal();
            lock.unlock();
        };

        Thread thread = new Thread(runnable, "mock-ui-thread-play");
        thread.start();
    }

    @Override
    public Card getChosenCard() {
        return hand.get(rand.nextInt(hand.size()));
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
        return true;
    }

    @Override
    public void displayStatusMessage(String message) {

    }

    @Override
    public void displayGameResult(Team winningTeam, boolean won) {

    }

    @Override
    public boolean getNewGame() {
        playedGames++;
        return playedGames < nbrGames;
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

    @Override
    public void displayMatch(Team team, boolean us) {

    }

    private void waitSec(float seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException ignored) {
        }
    }
}
