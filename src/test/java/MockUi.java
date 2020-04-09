import com.leflat.jass.common.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class MockUi implements IJassUi {
    private Random rand = new Random();
    private List<Card> hand;
    private IRemotePlayer player;

    public MockUi(IRemotePlayer player) {
        this.player = player;
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
        return rand.nextBoolean() ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing() {

    }

    @Override
    public void drawCard(Lock lock, Condition condition) {
        Runnable runnable = () -> {
            System.out.println("Inside : " + Thread.currentThread().getName());
            lock.lock();
            condition.signal();
            lock.unlock();
            System.out.println("Done : " + Thread.currentThread().getName());
        };

        Thread thread = new Thread(runnable, "mock-ui-thread-draw");

        thread.start();
    }


    @Override
    public int getDrawnCardPosition() {
        return rand.nextInt(36);
    }

    @Override
    public void setDrawnCard(int playerPosition, int cardPosition, Card card) throws IndexOutOfBoundsException {

    }

    @Override
    public BasePlayer choosePartner(List<BasePlayer> players) {
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
        return rand.nextInt(allowedToPass ? 5 : 4);
    }

    @Override
    public void setAtout(int atout, int positionOfPlayerToChooseAtout) {

    }

    @Override
    public void chooseCard(Lock lock, Condition condition) {
        Runnable runnable = () -> {
            System.out.println("Inside : " + Thread.currentThread().getName());
            System.out.println(lock);
            lock.lock();
            condition.signal();
            lock.unlock();
            System.out.println("Done : " + Thread.currentThread().getName());
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
        return rand.nextBoolean();
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
}