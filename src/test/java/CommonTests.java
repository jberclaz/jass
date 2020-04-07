import com.leflat.jass.client.ClientPlayer;
import com.leflat.jass.common.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class CommonTests {
    @Test
    public void card_test() {
        var card = new Card(Card.RANK_BOURG, Card.COLOR_SPADE);
        Card.atout = Card.COLOR_HEART;
        assertEquals(2, card.getValue());
        Card.atout = Card.COLOR_SPADE;
        assertEquals(20, card.getValue());
        assertEquals(Card.COLOR_SPADE, card.getColor());
        assertEquals(Card.RANK_BOURG, card.getRank());
        assertEquals("bourg de pique", card.toString());
        assertEquals(5, card.getNumber());
        var card2 = new Card(12);
        assertEquals(Card.RANK_NELL, card2.getRank());
        assertEquals(Card.COLOR_HEART, card2.getColor());
    }

    @Test
    public void anouncement_test() {
        var card = new Card(Card.RANK_DAME, Card.COLOR_CLUB);
        var anouncement = new Anouncement(Anouncement.FIFTY, card);
        assertEquals("cinquante à la dame de trèfle", anouncement.toString());
        assertEquals(50, anouncement.getValue());
        anouncement = new Anouncement(Anouncement.SQUARE, card);
        assertEquals("cent des dames", anouncement.toString());
        assertEquals(100, anouncement.getValue());
    }

    @Test
    public void find_square_test() {
        int[] list = {5, 9, 12, 14, 19, 23, 24, 32};
        var annoucements = Anouncement.findAnouncements(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(1, annoucements.size());
        var anouncement = annoucements.get(0);
        assertEquals(anouncement.getType(), Anouncement.BOURG_SQUARE);
        assertEquals(anouncement.getCard().getNumber(), 5);
    }

    @Test
    public void find_suit_test() {
        int[] list = {5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 32};
        var annoucements = Anouncement.findAnouncements(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(2, annoucements.size());
        var firstAnouncement = annoucements.get(0);
        assertEquals(firstAnouncement.getType(), Anouncement.THREE_CARDS);
        assertEquals(firstAnouncement.getCard().getNumber(), 14);
        var secondAnouncement = annoucements.get(1);
        assertEquals(secondAnouncement.getType(), Anouncement.FIFTY);
        assertEquals(secondAnouncement.getCard().getNumber(), 22);
    }

    @Test
    public void find_stoeck_test() {
        int[] list = {5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 25};
        assertFalse(Anouncement.findStoeck(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()), Card.COLOR_HEART));
        assertTrue(Anouncement.findStoeck(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()), Card.COLOR_DIAMOND));
    }

    @Test
    public void card_sort_test() {
        int[] list = {20, 30, 1, 3, 15, 12, 8, 35, 34};
        List<Card> hand = Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList());
        Card.sort(hand);
        for (int i=1; i<hand.size(); i++) {
            assertTrue(hand.get(i-1).getNumber() < hand.get(i).getNumber());
        }
    }

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

    /*
    @Test
    public void plie_play_card_test() {
        var player = new ClientPlayer(0);
        var atout = Card.COLOR_SPADE;
        var plie = new Plie(Card.COLOR_HEART, Card.RANK_8, false);
        try {
            plie.playCard(new Card(Card.RANK_10, Card.COLOR_HEART), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_10, plie.highest);
        assertEquals(10, plie.score);

        try {
            plie.playCard(new Card(Card.RANK_NELL, Card.COLOR_HEART), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_10, plie.highest);
        assertEquals(10, plie.score);

        try {
            plie.playCard(new Card(Card.RANK_AS, Card.COLOR_DIAMOND), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_10, plie.highest);
        assertEquals(21, plie.score);

        try {
            plie.playCard(new Card(Card.RANK_DAME, Card.COLOR_SPADE), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_DAME, plie.highest);
        assertTrue(plie.cut);
        assertEquals(24, plie.score);

        try {
            plie.playCard(new Card(Card.RANK_AS, Card.COLOR_SPADE), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_AS, plie.highest);
        assertEquals(35, plie.score);

        try {
            plie.playCard(new Card(Card.RANK_NELL, Card.COLOR_SPADE), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_NELL, plie.highest);
        assertEquals(49, plie.score);

        plie = new Plie(Card.COLOR_SPADE, Card.RANK_8, false);
        try {
            plie.playCard(new Card(Card.RANK_10, Card.COLOR_SPADE), atout, player);
        } catch (BrokenRuleException e) {
            e.printStackTrace();
        }
        assertEquals(Card.RANK_10, plie.highest);
        assertEquals(10, plie.score);
        assertFalse(plie.cut);
    }
*/

    @Test
    void shuffle_test() {
        var cards = Card.shuffle(10);
        assertEquals(10, cards.size());
        boolean[] presence = new boolean[10];
        for (var c : cards) {
            assertFalse(presence[c.getNumber()]);
            presence[c.getNumber()] = true;
        }
    }
}