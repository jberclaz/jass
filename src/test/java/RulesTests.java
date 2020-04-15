import com.leflat.jass.common.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class RulesTests {
    private TestPlayer firstPlayer = new TestPlayer(0);
    private TestPlayer secondPlayer = new TestPlayer(1);
    private Plie plie;

    @BeforeEach
    public void setUp() {
        Card.atout = Card.COLOR_DIAMOND;
        plie = new Plie(new Card(Card.RANK_DAME, Card.COLOR_HEART), firstPlayer);
    }

    @Test
    public void test_bourg_sec() {
        Card.atout = Card.COLOR_SPADE;
        var hand = buildHand(Card.RANK_6, Card.RANK_8, Card.RANK_BOURG, 20, 21, 22);
        assertFalse(Rules.hasBourgSec(hand));

        hand = buildHand(Card.RANK_BOURG, 20, 21, 22, 28, 29, 30);
        assertTrue(Rules.hasBourgSec(hand));
    }

    @Test
    public void test_has_color() {
        var hand = buildHand(1, 3, 10, 12, 20, 30, 31);
        assertTrue(Rules.hasColor(hand, Card.COLOR_HEART));

        hand = buildHand(1, 3, 4, 8, 20, 30, 31, 32);
        assertFalse(Rules.hasColor(hand, Card.COLOR_HEART));
    }

    @Test
    public void test_plie_first_card() {
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_DAME, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(1, plie.getSize());
    }

    @Test
    public void test_follow_under() throws BrokenRuleException {
        var hand = buildHand(9, 18, 27);
        plie.playCard(new Card(Card.RANK_NELL, Card.COLOR_HEART), secondPlayer, hand);
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_DAME, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    public void test_follow_above() throws BrokenRuleException {
        var hand = buildHand(9, 17, 18, 27);
        plie.playCard(new Card(Card.RANK_AS, Card.COLOR_HEART), secondPlayer, hand);
        assertEquals(secondPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_AS, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(14, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    public void test_cut() throws BrokenRuleException {
        var hand = buildHand(9, 17, 18, 27);
        plie.playCard(new Card(Card.RANK_6, Card.COLOR_DIAMOND), secondPlayer, hand);
        assertEquals(secondPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_6, plie.getHighest());
        assertTrue(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    public void test_not_follow() throws BrokenRuleException {
        var hand = buildHand(4, 18, 27);
        plie.playCard(new Card(Card.RANK_10, Card.COLOR_SPADE), secondPlayer, hand);
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_DAME, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(13, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    public void test_not_follow_break() {
        var hand = buildHand(4, 9, 10, 18, 27);
        Assertions.assertThrows(BrokenRuleException.class, () -> {
            plie.playCard(new Card(Card.RANK_10, Card.COLOR_SPADE), secondPlayer, hand);
        });
    }

    @Test
    void test_follow_atout() throws BrokenRuleException {
        Card.atout = Card.COLOR_HEART;
        var hand = buildHand(9, 18, 27);
        plie.playCard(new Card(Card.RANK_NELL, Card.COLOR_HEART), secondPlayer, hand);
        assertEquals(secondPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_NELL, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(17, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    void test_first_card() throws BrokenRuleException {
        var plie = new Plie();
        var card = new Card(0);
        plie.playCard(card, firstPlayer, new ArrayList<>());
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_SPADE, plie.getColor());
        assertEquals(Card.RANK_6, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(0, plie.getScore());
        assertEquals(1, plie.getSize());
    }

    @Test
    void test_over_cut() throws BrokenRuleException {
        var hand = buildHand(9, 17, 18, 27);
        plie.playCard(new Card(Card.RANK_6, Card.COLOR_DIAMOND), firstPlayer, hand);
        plie.playCard(new Card(Card.RANK_8, Card.COLOR_DIAMOND), secondPlayer, hand);
        assertEquals(secondPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_8, plie.getHighest());
        assertTrue(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(3, plie.getSize());
    }

    @Test
    void test_under_cut_break() throws BrokenRuleException {
        var hand = buildHand(9, 17, 18, 27);
        plie.playCard(new Card(Card.RANK_10, Card.COLOR_DIAMOND), firstPlayer, hand);
        Assertions.assertThrows(BrokenRuleException.class, () -> {
            plie.playCard(new Card(Card.RANK_8, Card.COLOR_DIAMOND), secondPlayer, hand);
        });
    }

    @Test
    void test_under_cut_valid() throws BrokenRuleException {
        var hand = buildHand(18, 19, 20, 22);
        plie.playCard(new Card(Card.RANK_AS, Card.COLOR_DIAMOND), firstPlayer, new ArrayList<>());
        plie.playCard(new Card(Card.RANK_8, Card.COLOR_DIAMOND), secondPlayer, hand);
    }

    @Test
    void test_under_cut_break2() throws BrokenRuleException {
        var hand = buildHand(18, 19, 20, 21);
        plie.playCard(new Card(Card.RANK_AS, Card.COLOR_DIAMOND), firstPlayer, new ArrayList<>());
        Assertions.assertThrows(BrokenRuleException.class, () -> {
            plie.playCard(new Card(Card.RANK_8, Card.COLOR_DIAMOND), secondPlayer, hand);
        });
    }

    @Test
    void test_play_with_bourg_sec() throws BrokenRuleException {
        Card.atout = Card.COLOR_HEART;
        var bourg = new Card(Card.RANK_BOURG, Card.COLOR_HEART);
        var hand = buildHand(0, 1, bourg.getNumber(), 25, 30);
        plie.playCard(new Card(Card.RANK_6, Card.COLOR_SPADE), secondPlayer, hand);
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_DAME, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    public static List<Card> buildHand(int... numbers) {
        return Arrays.stream(numbers).mapToObj(Card::new).collect(Collectors.toList());
    }
}
