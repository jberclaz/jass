import com.leflat.jass.common.*;
import com.leflat.jass.server.AbstractRemotePlayer;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MockRemotePlayer extends AbstractRemotePlayer {
    private Random rand = new Random();
    private Plie plie = new Plie();
    private List<Anouncement> anouncements = new ArrayList<>();

    public MockRemotePlayer(int id, String name) {
        super(id);
        this.name = name;
    }

    @Override
    public void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection {

    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() throws PlayerLeftExpection {
        return rand.nextBoolean() ? TeamSelectionMethod.RANDOM : TeamSelectionMethod.MANUAL;
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) throws PlayerLeftExpection {

    }

    @Override
    public int drawCard() {
        return rand.nextInt(36);
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) throws PlayerLeftExpection {

    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) throws PlayerLeftExpection {

    }

    @Override
    public int choosePartner() throws PlayerLeftExpection {
        return rand.nextInt(4);
    }

    @Override
    public int chooseAtout(boolean first) throws PlayerLeftExpection {
        return rand.nextInt(first ? 5 : 4);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {

    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        Card.sort(cards);
        super.setHand(cards);
    }

    @Override
    public Card play() throws PlayerLeftExpection {
        if (hand.size() == 9) {
            if (rand.nextBoolean()) {
                anouncements = Anouncement.findAnouncements(hand);
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
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {
        try {
            plie.playCard(card, player, null);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void collectPlie(BasePlayer player) throws PlayerLeftExpection {
        plie = new Plie();
        anouncements.clear();
    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {

    }

    @Override
    public List<Anouncement> getAnoucement() throws PlayerLeftExpection {
        return anouncements;
    }

    @Override
    public void setAnouncement(BasePlayer player, List<Anouncement> anouncements) throws PlayerLeftExpection {

    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {

    }

    @Override
    public boolean getNewGame() {
        return false;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {

    }
}
