import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ServerTests {
    @Test
    public void card_test() {
        var card = new Card(Card.RANK_BOURG, Card.COLOR_SPADE);
        assertEquals(2, card.getValue());
        assertEquals(20, card.getValue(Card.COLOR_SPADE));
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
        assertEquals("cinquante au dame de trefle", anouncement.toString());
        assertEquals(50, anouncement.getValue());
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
}
