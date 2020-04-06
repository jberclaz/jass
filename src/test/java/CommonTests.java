import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.common.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class CommonTests {
    @Test
    public void team_score_test() {
        var team = new Team(0);
        var ans = new ArrayList<Anouncement>();
        ans.add(new Anouncement(Anouncement.NELL_SQUARE, new Card(5)));
        ans.add(new Anouncement(Anouncement.THREE_CARDS, new Card(12)));

        team.addAnnoucementScore(ans, Card.COLOR_HEART);
        assertEquals(170, team.getScore());

        team.resetScore();
        team.addAnnoucementScore(ans, Card.COLOR_SPADE);
        assertEquals(340, team.getScore());

        assertFalse(team.hasWon());

        team.addScore(1200);
        assertTrue(team.hasWon());
    }

    @Test
    public void plie_play_card_test() {
        var player = new ClientPlayer(0);
        var atout = Card.COLOR_SPADE;
        var plie = new Plie(Card.COLOR_HEART, Card.RANK_8, false);
        try {
            plie.playCard(new Card(Card.COLOR_HEART, Card.RANK_10), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
    }
}
