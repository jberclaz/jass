import com.leflat.jass.common.Card;
import com.leflat.jass.common.Plie;
import com.leflat.jass.common.Rules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class RulesTests {
    @Test
    public void test_bourg_sec() {
        var hand = buildHand(new int[]{Card.RANK_6, Card.RANK_8, Card.RANK_BOURG, 20, 21, 22});
        assertFalse(Rules.hasBourgSec(hand, Card.COLOR_SPADE));

        hand = buildHand(new int[]{Card.RANK_BOURG, 20, 21, 22, 28, 29, 30});
        assertTrue(Rules.hasBourgSec(hand, Card.COLOR_SPADE));
    }

    @Test
    public void test_has_color() {
        var hand = buildHand(new int[]{1, 3, 10, 12, 20, 30, 31});
        assertTrue(Rules.hasColor(hand, Card.COLOR_HEART));

        hand = buildHand(new int[]{1, 3, 4, 8, 20, 30, 31, 32});
        assertFalse(Rules.hasColor(hand, Card.COLOR_HEART));
    }

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

    private List<Card> buildHand(int[] numbers) {
        return Arrays.stream(numbers).mapToObj(Card::new).collect(Collectors.toList());
    }
}
