package com.leflat.jass.server;

import com.leflat.jass.common.Announcement;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.leflat.jass.server.RulesTests.buildHand;
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
        var cards = anouncement.getCards();
        assertEquals(cards.length, 4);
        for (int i=0; i<4; i++) {
            assertEquals(cards[i], new Card(Card.RANK_NELL, i));
        }

        anouncement = new Announcement(Announcement.STOECK, null);
        assertEquals("stoeck", anouncement.toString());
        cards = anouncement.getCards();
        assertEquals(cards.length, 2);
        assertEquals(cards[0], new Card(Card.RANK_DAME, Card.atout));
        assertEquals(cards[1], new Card(Card.RANK_ROI, Card.atout));
        assertEquals(20, anouncement.getValue());

        assertThrows(RuntimeException.class, () -> {
            var a = new Announcement(19, null);
            a.getCards();
        });
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

        int[] list2 = {4, 9, 12, 13, 19, 22, 24, 31};
        annoucements = Announcement.findAnouncements(Arrays.stream(list2).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(1, annoucements.size());
        anouncement = annoucements.get(0);
        assertEquals(anouncement.getType(), Announcement.SQUARE);

        int[] list3 = {3, 9, 10, 12, 19, 21, 24, 30};
        annoucements = Announcement.findAnouncements(Arrays.stream(list3).mapToObj(Card::new).collect(Collectors.toList()));
        assertEquals(1, annoucements.size());
        anouncement = annoucements.get(0);
        assertEquals(anouncement.getType(), Announcement.NELL_SQUARE);
    }

    @Test
    public void find_suit_test() {
        var annoucements = Announcement.findAnouncements(buildHand(5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 32));
        assertEquals(2, annoucements.size());
        var firstAnouncement = annoucements.get(1);
        assertEquals(firstAnouncement.getType(), Announcement.THREE_CARDS);
        assertEquals(firstAnouncement.getCard().getNumber(), 14);
        var secondAnouncement = annoucements.get(0);
        assertEquals(secondAnouncement.getType(), Announcement.FIFTY);
        assertEquals(secondAnouncement.getCard().getNumber(), 22);

        annoucements = Announcement.findAnouncements(buildHand(0, 5, 19, 21, 22, 26, 28, 31, 33));
        assertEquals(0, annoucements.size());

        annoucements = Announcement.findAnouncements(buildHand(0, 1, 2, 3, 4, 20, 22, 24, 26));
        assertEquals(1, annoucements.size());
        assertEquals(annoucements.get(0).getType(), Announcement.HUNDRED);
        assertEquals(annoucements.get(0).getCard().getNumber(), 4);

        annoucements = Announcement.findAnouncements(buildHand(0, 1, 2, 3, 4, 5, 22, 24, 26));
        assertEquals(1, annoucements.size());
        assertEquals(annoucements.get(0).getType(), Announcement.HUNDRED);
        assertEquals(annoucements.get(0).getCard().getNumber(), 5);

        annoucements = Announcement.findAnouncements(buildHand(0, 1, 2, 3, 4, 5, 6, 7, 8));
        assertEquals(2, annoucements.size());
        assertEquals(Announcement.HUNDRED, annoucements.get(0).getType());
        assertEquals(8, annoucements.get(0).getCard().getNumber());
        assertEquals(Announcement.FIFTY, annoucements.get(1).getType());
        assertEquals(3, annoucements.get(1).getCard().getNumber());
    }

    @Test
    public void find_stoeck_test() {
        var cards = buildHand(5, 9, 12, 13, 14, 19, 20, 21, 22, 24, 25);
        Card.atout = Card.COLOR_HEART;
        assertFalse(Announcement.findStoeck(cards));
        Card.atout = Card.COLOR_CLUB;
        assertTrue(Announcement.findStoeck(cards));
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
        team.addAnnouncementScore(ans);
        assertEquals(170, team.getScore());

        team.resetScore();
        Card.atout = Card.COLOR_SPADE;
        team.addAnnouncementScore(ans);
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