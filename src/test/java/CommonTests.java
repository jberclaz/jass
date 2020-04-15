import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Team;
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
        var anouncement = new Announcement(Announcement.FIFTY, card);
        assertEquals("cinquante à la dame de trèfle", anouncement.toString());
        assertEquals(50, anouncement.getValue());
        anouncement = new Announcement(Announcement.SQUARE, card);
        assertEquals("cent des dames", anouncement.toString());
        assertEquals(100, anouncement.getValue());
        anouncement = new Announcement(Announcement.NELL_SQUARE, new Card(Card.RANK_NELL, Card.COLOR_HEART));
        assertEquals("cent cinquante des nells", anouncement.toString());
        assertEquals(150, anouncement.getValue());
    }

    @Test
    public void find_square_test() {
        int[] list = {5, 9, 12, 14, 19, 23, 24, 32};
        var annoucements = Announcement.findAnouncements(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(1, annoucements.size());
        var anouncement = annoucements.get(0);
        assertEquals(anouncement.getType(), Announcement.BOURG_SQUARE);
        assertEquals(anouncement.getCard().getRank(), Card.RANK_BOURG);
        assertEquals(200, anouncement.getValue());
    }

    @Test
    public void find_suit_test() {
        int[] list = {5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 32};
        var annoucements = Announcement.findAnouncements(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(2, annoucements.size());
        var firstAnouncement = annoucements.get(0);
        assertEquals(firstAnouncement.getType(), Announcement.THREE_CARDS);
        assertEquals(firstAnouncement.getCard().getNumber(), 14);
        var secondAnouncement = annoucements.get(1);
        assertEquals(secondAnouncement.getType(), Announcement.FIFTY);
        assertEquals(secondAnouncement.getCard().getNumber(), 22);
    }

    @Test
    public void find_stoeck_test() {
        int[] list = {5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 25};
        Card.atout = Card.COLOR_HEART;
        assertFalse(Announcement.findStoeck(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList())));
        Card.atout = Card.COLOR_DIAMOND;
        assertTrue(Announcement.findStoeck(Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList())));
    }

    @Test
    public void card_sort_test() {
        int[] list = {20, 30, 1, 3, 15, 12, 8, 35, 34};
        List<Card> hand = Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList());
        Card.sort(hand);
        for (int i = 1; i < hand.size(); i++) {
            assertTrue(hand.get(i - 1).getNumber() < hand.get(i).getNumber());
        }
    }

    @Test
    public void team_score_test() {
        var team = new Team(0);
        var ans = new ArrayList<Announcement>();
        ans.add(new Announcement(Announcement.NELL_SQUARE, new Card(5)));
        ans.add(new Announcement(Announcement.THREE_CARDS, new Card(12)));

        Card.atout = Card.COLOR_HEART;
        team.addAnnoucementScore(ans);
        assertEquals(170, team.getScore());

        team.resetScore();
        Card.atout = Card.COLOR_SPADE;
        team.addAnnoucementScore(ans);
        assertEquals(340, team.getScore());

        assertFalse(team.hasWon());

        team.addScore(1200);
        assertTrue(team.hasWon());
    }

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

    @Test
    void test_announce_comparison() {
        Card.atout = Card.COLOR_SPADE;
        var a = new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_DIAMOND));
        assertEquals(a, new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_DIAMOND)));
        assertEquals(a, a);
        assertNotEquals(a, new Card(0));
        assertNotEquals(a, new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_CLUB)));
        assertNotEquals(a, new Announcement(Announcement.FIFTY, new Card(Card.RANK_BOURG, Card.COLOR_DIAMOND)));
        assertTrue(a.compareTo(new Announcement(Announcement.FIFTY, new Card(Card.RANK_ROI, Card.COLOR_CLUB))) < 0);
        assertTrue(a.compareTo(new Announcement(Announcement.FIFTY, new Card(Card.RANK_BOURG, Card.COLOR_CLUB))) > 0);
        assertEquals(0, a.compareTo(new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_CLUB))));
        assertTrue(a.compareTo(new Announcement(Announcement.HUNDRED, new Card(Card.RANK_DAME, Card.COLOR_CLUB))) < 0);
        assertTrue(a.compareTo(new Announcement(Announcement.THREE_CARDS, new Card(Card.RANK_DAME, Card.COLOR_CLUB))) > 0);
        Card.atout = Card.COLOR_CLUB;
        assertTrue(a.compareTo(new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_CLUB))) < 0);
        Card.atout = Card.COLOR_DIAMOND;
        assertTrue(a.compareTo(new Announcement(Announcement.FIFTY, new Card(Card.RANK_DAME, Card.COLOR_CLUB))) > 0);
    }

    @Test
    void test_card_comparison() {
        var c = new Card(Card.RANK_NELL, Card.COLOR_HEART);
        assertEquals(c, c);
        assertNotEquals(c, 4);
        assertEquals(c, new Card(Card.RANK_NELL, Card.COLOR_HEART));
        assertNotEquals(c, new Card(Card.RANK_NELL, Card.COLOR_SPADE));
        assertNotEquals(c, new Card(Card.RANK_10, Card.COLOR_HEART));
        assertEquals(0, c.compareTo(new Card(Card.RANK_NELL, Card.COLOR_HEART)));
        assertThrows(ClassCastException.class, () -> {
            c.compareTo(new Card(Card.RANK_NELL, Card.COLOR_CLUB));
        });
    }
}