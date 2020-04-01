import static org.junit.jupiter.api.Assertions.assertEquals;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;
import org.junit.jupiter.api.Test;

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
}
