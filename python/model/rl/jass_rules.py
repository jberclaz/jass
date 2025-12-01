"""
A Python translation of the core Jass game logic from Card.java and Plie.java.

This module provides:
- The `Card` class and all its associated constants.
- The `Plie` class, which encapsulates the logic for playing a trick.
- The `BrokenRuleException` for error handling.
- A `BasePlayer` stub for type-hinting and ownership.

Note: The global trump suit must be set via `jass_rules.ATOUT = <your_trump_color>`
before calling `get_value()` or `play_card()` for correct scoring and comparison.
"""
import collections
from dataclasses import dataclass
from enum import IntEnum
from typing import Iterable


class PlayerPosition(IntEnum):
    SELF = 0
    RIGHT = 1
    ACROSS = 2
    LEFT = 3

class Suit(IntEnum):
    """
    Represents the four Jass suits.
    Values 0-3 map directly to the logic (card_id // 9).
    You can use Swiss or French names.
    """
    SPADE = 0
    HEART = 1
    CLUB = 2
    DIAMOND = 3
    PASS = 4
    NONE = 5

# --- Constants from Card.java ---
DECK_SIZE = 36
RANK_6 = 0
RANK_7 = 1
RANK_8 = 2
RANK_NELL = 3
RANK_10 = 4
RANK_BOURG = 5  # Jack
RANK_DAME = 6  # Queen
RANK_ROI = 7  # King
RANK_AS = 8

TRUMP_RANK_MAP = {
    RANK_6: RANK_6,
    RANK_7: RANK_7,
    RANK_8: RANK_8,
    RANK_NELL: 9,
    RANK_10: RANK_10,
    RANK_BOURG: 10,
    RANK_DAME: RANK_DAME,
    RANK_ROI: RANK_ROI,
    RANK_AS: RANK_AS,
}

TRUMP_RANK_LIST = [RANK_6, RANK_7, RANK_8, 9, RANK_10, 10, RANK_DAME, RANK_ROI, RANK_AS]

DIAMOND_SEVEN = Suit.DIAMOND * 9 + RANK_7

RANK_NAMES = ["six", "sept", "huit", "nell", "dix", "bourg", "dame", "roi", "as"]
COLOR_NAMES = ["pique", "cœur", "trèfle", "carreau"]

VALUES = [0, 0, 0, 0, 10, 2, 3, 4, 11]
VALUES_ATOUT = [0, 0, 0, 14, 10, 20, 3, 4, 11]

# This is a module-level global, just like `Card.atout` in Java.
# You MUST set this from your environment (e.g., jass_rules.ATOUT = jass_rules.COLOR_SPADE)
#ATOUT = Suit.NONE

WINNING_SCORE = 1500


class Card:
    """
    Python representation of a Jass card, wrapping an integer (0-35).
    """

    def __init__(self, number: int):
        if not (0 <= number < DECK_SIZE):
            raise ValueError(f"Invalid card number: {number}")
        self._number = number

    @classmethod
    def from_rank_color(cls, rank: int, color: Suit) -> 'Card':
        """Creates a card from its rank and color."""
        return cls(color.value * 9 + rank)

    @property
    def suit(self) -> Suit:
        return Suit(self._number // 9)

    @property
    def rank(self) -> int:
        return self._number % 9

    @property
    def number(self) -> int:
        return self._number

    def get_value(self, trump: Suit) -> int:
        """Gets the point value of the card, respecting the global Card.TRUMP."""
        if self.suit == trump:
            return VALUES_ATOUT[self.rank]
        return VALUES[self.rank]

    def _get_rank(self, trump: Suit):
        rank = self.rank
        if self.suit != trump:
            return rank
        return TRUMP_RANK_MAP[rank]

    @staticmethod
    def compare(a: 'Card', b: 'Card', trump: Suit):
        assert a.suit == b.suit
        return a._get_rank(trump) - b._get_rank(trump)

    def __eq__(self, other):
        if not isinstance(other, Card):
            return NotImplemented
        return self._number == other._number

    def __hash__(self):
        return hash(self._number)

    def __repr__(self) -> str:
        return f"Card({RANK_NAMES[self.rank]} de {COLOR_NAMES[self.suit]})"

ANNOUNCE_NONE = 0
ANNOUNCE_THREE = 1   # Tierce (3 cards in sequence)
ANNOUNCE_FIFTY = 2  # Quarte (4 cards in sequence)
ANNOUNCE_HUNDRED = 3  # Quinte (5 cards in sequence)
ANNOUNCE_CARRE = 4   # Carré (4 of a kind, other than Jacks/Nells)
ANNOUNCE_NELL = 5 # Carré de Nells (4 Nells)
ANNOUNCE_BOURGS = 6 # Carré de Bourgs (4 Jacks)
ANNOUNCE_STOECK = 7

ANNOUNCE_VALUES = {
    ANNOUNCE_THREE: 20,
    ANNOUNCE_FIFTY: 50,
    ANNOUNCE_HUNDRED: 100,
    ANNOUNCE_CARRE: 100,
    ANNOUNCE_NELL: 150,
    ANNOUNCE_BOURGS: 200,
    ANNOUNCE_STOECK: 20,
}
# ... (rest of jass_rules.py) ...


@dataclass(frozen=True)
class Announcement:
    """
    Represents a single announcement made by a player in Jass.

    The ordering logic ensures that a higher-value or higher-ranking announcement
    takes precedence over others of the same type.

    Attributes:
        type (int): The type of announcement (ANNOUNCE_TIER, ANNOUNCE_CARR, etc.).
        highest_card (int): The ID (0-35) of the highest card in the sequence or set.
    """
    type: int
    highest_card: Card

    def __lt__(self, other: 'Announcement') -> bool:
        """
        Defines the complex Jass announcement hierarchy logic.

        Rule 1: Higher value wins.
        Rule 2: If values are equal, the one with the higher 'highest card' wins.
        """
        if not isinstance(other, Announcement):
            return NotImplemented

        # Rule 1: Higher type
        if self.type != other.type:
            return self.type < other.type

        return self.highest_card.rank < other.highest_card.rank

    def __gt__(self, other: 'Announcement') -> bool:
        if not isinstance(other, Announcement):
            return NotImplemented

        if self.type != other.type:
            return self.type > other.type

        return self.highest_card.rank > other.highest_card.rank

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Announcement):
            return NotImplemented
        # Announcements are equal if all three properties match
        return (self.type == other.type and
                self.highest_card.rank == other.highest_card.rank)

    @property
    def value(self):
        return ANNOUNCE_VALUES[self.type]

    @property
    def card_ids(self) -> Iterable[int]:
        if self.type in [ANNOUNCE_THREE, ANNOUNCE_FIFTY, ANNOUNCE_HUNDRED]:
            for rank in range(self.type + 2):
                yield self.highest_card.rank - rank
        elif self.type in [ANNOUNCE_CARRE, ANNOUNCE_NELL, ANNOUNCE_BOURGS]:
            for suit in range(4):
                yield self.highest_card.rank + suit * 9
        for rank in [RANK_DAME, RANK_ROI]:
            yield self.highest_card.suit * 9 + rank

    @staticmethod
    def _find_squares(hand: list[Card]) -> list['Announcement']:
        """
        Equivalent to Java's private static Collection<Announcement> findSquares(List<Card> hand).
        Finds Carrés (Squares).
        """
        # 1. Count the rank of each card
        # A dictionary is a good Python equivalent for an array used as a map/count.
        # The Java code assumes ranks are 0-8. Let's assume Card.getRank() returns an integer rank.
        rank_count = collections.defaultdict(int)
        for card in hand:
            rank_count[card.rank] += 1

        announcements = []

        # 2. Iterate through ranks to check for a count of 4 (a Square)
        # The Java code iterates from RANK_NELL to RANK_AS (assuming these are ordered integers).
        # We will use the keys from rank_count to only check ranks present in the hand.
        # We'll use the original Java loop bounds for strict translation if possible:
        # for rank in range(Card.RANK_NELL, Card.RANK_AS + 1):

        # Simpler Pythonic iteration over counts:
        for rank, count in rank_count.items():
            if rank in [0, 1, 2]:
                continue # squares of 6, 7 and 8 do not count
            if count == 4:
                # Determine the type of Square
                announcement_type = ANNOUNCE_CARRE
                if rank == RANK_NELL:
                    announcement_type = ANNOUNCE_NELL
                elif rank == RANK_BOURG:
                    announcement_type = ANNOUNCE_BOURGS

                # Create a Card object for the announcement (color doesn't matter for squares,
                # but Java used Card.COLOR_SPADE)
                announcements.append(Announcement(announcement_type, Card.from_rank_color(rank, Suit.SPADE)))

        return announcements

    @staticmethod
    def _find_suits(hand: list[Card]) -> list['Announcement']:
        """
        Equivalent to Java's private static Collection<Announcement> findSuits(List<Card> hand).
        Finds Suites (Runs/Sequences: 3-cartes, Cinquante, Cent).

        NOTE: This implementation relies on the 'hand' being sorted
        in a specific way for the color and number comparisons to work correctly,
        which is typical in Jass card game implementations.
        The Java code iterates backwards, suggesting a sort order (e.g., by Card.getNumber() descending).
        We assume the input 'hand' is already sorted as required.
        """
        announcements = []
        i = len(hand) - 1
        while i >= 2:  # Need at least 3 cards remaining to potentially form a 3-card suit
            first_card = hand[i]
            color = first_card.suit
            j = i - 1
            nbr_cards = 1

            # Check for sequential cards of the same color
            while (j >= 0) and (hand[j].suit == color):
                # The number check is the core logic: card at j's number should be exactly
                # (j - i) less than first_card's number, ensuring they form a sequence.
                # e.g., if i=5, j=4, then j-i = -1. card at 4 should be 1 less than card at 5.
                if hand[j].number == (first_card.number + j - i):
                    nbr_cards += 1
                else:
                    break
                j -= 1

            if nbr_cards >= 3:  # Found a run (3, 4, or 5 cards)
                # Announcement type is based on length: 3 cards (type 1), 4 cards (type 2), 5+ cards (type 3)
                # Type: THREE_CARDS (1), FIFTY (2), HUNDRED (3)
                # nbrCards = 3 -> type = 3-2 = 1 (THREE_CARDS)
                # nbrCards = 5 -> type = 5-2 = 3 (HUNDRED)
                nbr_cards = min(nbr_cards, 5)  # Max announcement length is 5 (for Cent/HUNDRED)

                # The type is nbrCards - 2:
                announcement_type = nbr_cards - 2

                # The announcement is created with the highest card of the suit (hand[i])
                announcements.append(Announcement(announcement_type, hand[i]))

                # Skip the cards used in this announcement:
                i -= nbr_cards - 1
            else:
                i -= 1  # No suit found ending at 'i', move to the next card

        return announcements

    @staticmethod
    def find_announcements(hand: list[Card]) -> list['Announcement']:
        """
        Equivalent to Java's public static List<Announcement> findAnouncements(List<Card> hand).
        Finds all Carrés (Squares) and Suites (Runs) in the hand.

        NOTE: The 'Stoeck' announcement is found by a separate method in the Java file
        ('findStoeck') and is usually handled outside this main function during the game
        logic (often implicitly by having the King and Queen of the trump suit).
        This function only finds Squares and Suits, matching the Java source.
        """

        sorted_hand = sorted(hand, key=lambda c: c.number)

        # 1. Find all Square announcements
        squares = Announcement._find_squares(sorted_hand)

        # 2. Find all Suit announcements (3-cartes, cinquante, cent)
        # NOTE: The Java findSuits relies on the hand being pre-sorted.
        # Ensure 'hand' is sorted correctly before calling this in your Python implementation.
        suits = Announcement._find_suits(sorted_hand)

        # 3. Combine the results
        announcements = []
        announcements.extend(squares)
        announcements.extend(suits)

        return announcements
