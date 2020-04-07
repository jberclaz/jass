import com.leflat.jass.common.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var hand = buildHand(new int[]{Card.RANK_6, Card.RANK_8, Card.RANK_BOURG, 20, 21, 22});
        assertFalse(Rules.hasBourgSec(hand));

        hand = buildHand(new int[]{Card.RANK_BOURG, 20, 21, 22, 28, 29, 30});
        assertTrue(Rules.hasBourgSec(hand));
    }

    @Test
    public void test_has_color() {
        var hand = buildHand(new int[]{1, 3, 10, 12, 20, 30, 31});
        assertTrue(Rules.hasColor(hand, Card.COLOR_HEART));

        hand = buildHand(new int[]{1, 3, 4, 8, 20, 30, 31, 32});
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
        var hand = buildHand(new int[]{9, 18, 27});
        plie.playCard(new Card(Card.RANK_6, Card.COLOR_HEART), secondPlayer, hand);
        assertEquals(firstPlayer, plie.getOwner());
        assertEquals(Card.COLOR_HEART, plie.getColor());
        assertEquals(Card.RANK_DAME, plie.getHighest());
        assertFalse(plie.isCut());
        assertEquals(3, plie.getScore());
        assertEquals(2, plie.getSize());
    }

    @Test
    public void test_follow_above() throws BrokenRuleException {
        var hand = buildHand(new int[]{9, 17, 18, 27});
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
        var hand = buildHand(new int[]{9, 17, 18, 27});
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
        var hand = buildHand(new int[]{4, 18, 27});
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
        var hand = buildHand(new int[]{4, 9, 10, 18, 27});
        Assertions.assertThrows(BrokenRuleException.class, () -> {
            plie.playCard(new Card(Card.RANK_10, Card.COLOR_SPADE), secondPlayer, hand);
        });
    }


    /*
    @Test
    public void test_can_play() {
        var hand = buildHand(new int[]{1, 3, 10, 12, 20});
        var card = new Card(1);
        var plie = new Plie(-1, -1, false);
        int atout = Card.COLOR_CLUB;
        assertEquals(Rules.RULES_OK, Rules.canPlay(card, plie, hand, atout));

        plie = new Plie(Card.COLOR_SPADE, Card.RANK_DAME, false);
        assertEquals(Rules.RULES_OK, Rules.canPlay(card, plie, hand, atout));

        atout = Card.COLOR_HEART;
        card = new Card(10);
        assertEquals(Rules.RULES_OK, Rules.canPlay(card, plie, hand, atout));

        plie.cut = true;
        card = new Card(12);
        assertEquals(Rules.RULES_OK, Rules.canPlay(card, plie, hand, atout));

        card = new Card(10);
        assertEquals(Rules.RULES_CANNOT_UNDERCUT, Rules.canPlay(card, plie, hand, atout));

        plie.cut = false;
        card = new Card(20);
        assertEquals(Rules.RULES_MUST_FOLLOW, Rules.canPlay(card, plie, hand, atout));

        plie.color = Card.COLOR_CLUB;
        assertEquals(Rules.RULES_OK, Rules.canPlay(card, plie, hand, atout));
    }
*/

    private List<Card> buildHand(int[] numbers) {
        return Arrays.stream(numbers).mapToObj(Card::new).collect(Collectors.toList());
    }
}
