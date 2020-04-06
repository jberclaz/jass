import com.leflat.jass.common.Card;
import com.leflat.jass.common.Rules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class RulesTests {
    @Test
    public void test_bourg_sec() {
        int[] list = {Card.RANK_6, Card.RANK_8, Card.RANK_BOURG, 20, 21, 22};
        List<Card> hand = Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList());
        assertFalse(Rules.hasBourSec(hand, Card.COLOR_SPADE));

        int[] list2 = {Card.RANK_BOURG, 20, 21, 22, 28, 29, 30};
        List<Card> hand2 = Arrays.stream(list2).mapToObj(Card::new).collect(Collectors.toList());
        assertTrue(Rules.hasBourSec(hand2, Card.COLOR_SPADE));
    }

    @Test
    public void test_has_color() {
        int[] list = {1, 3, 10, 12, 20, 30, 31};
        List<Card> hand = Arrays.stream(list).mapToObj(Card::new).collect(Collectors.toList());
        assertTrue(Rules.hasColor(hand, Card.COLOR_HEART));

        int[] list2 = {1, 3, 4, 8, 20, 30, 31, 32};
        List<Card> hand2 = Arrays.stream(list2).mapToObj(Card::new).collect(Collectors.toList());
        assertFalse(Rules.hasColor(hand2, Card.COLOR_HEART));
    }
}
