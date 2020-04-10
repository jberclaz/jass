import com.leflat.jass.common.*;
import com.leflat.jass.server.AbstractRemotePlayer;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MockRemotePlayer extends AbstractRemotePlayer {
    private Random rand = new Random();
    private Plie plie = new Plie();
    private List<Announcement> anouncements = new ArrayList<>();

    public MockRemotePlayer(int id, String name) {
        super(id);
        this.name = name;
    }

    @Override
    public void setPlayerInfo(BasePlayer player) {

    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() {
        return rand.nextBoolean() ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) {

    }

    @Override
    public int drawCard() {
        return rand.nextInt(36);
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) {

    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) {

    }

    @Override
    public int choosePartner() {
        int partner;
        do {
            partner = rand.nextInt(4);
        } while (partner == id);
        return partner;
    }

    @Override
    public int chooseAtout(boolean first) {
        return rand.nextInt(first ? 5 : 4);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) {

    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        Card.sort(cards);
        super.setHand(cards);
    }

    @Override
    public Card play() {
        if (hand.size() == 9) {
            if (rand.nextBoolean()) {
                anouncements = Announcement.findAnouncements(hand);
            }
        }
        Card card;
        do {
            try {
                card = hand.get(rand.nextInt(hand.size()));
                plie.playCard(card, this, hand);
                break;
            } catch (BrokenRuleException ignored) {
            }
        } while (true);
        return card;
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) {
        try {
            plie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void collectPlie(BasePlayer player) {
        plie = new Plie();
        anouncements.clear();
    }

    @Override
    public void setScores(int score, int opponentScore) {

    }

    @Override
    public List<Announcement> getAnnouncements() {
        return anouncements;
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) {

    }

    @Override
    public void setGameResult(Team winningTeam) {

    }

    @Override
    public boolean getNewGame() {
        return false;
    }

    @Override
    public void playerLeft(BasePlayer player) {

    }
}
