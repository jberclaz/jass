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

import functools
from dataclasses import dataclass
from enum import IntEnum


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
    NONE = 4

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
    def from_rank_color(cls, rank: int, color: int) -> 'Card':
        """Creates a card from its rank and color."""
        return cls(color * 9 + rank)

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
        if rank == RANK_NELL:
            return RANK_AS + 1
        if rank == RANK_BOURG:
            return RANK_AS + 2
        return rank

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
ANNOUNCE_BOURGS = 4 # Carré de Bourgs (4 Jacks)
ANNOUNCE_CARRE = 5   # Carré (4 of a kind, other than Jacks/Nells)
ANNOUNCE_NELL = 6 # Carré de Nells (4 Nells)
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
        value (int): The points scored (20, 50, 100, etc.).
        highest_card_id (int): The ID (0-35) of the highest card in the sequence or set.
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

        # Rule 1: Higher value wins
        if self.value != other.value:
            return self.value < other.value

        # Rule 2: If values are equal, highest card wins (used for Tiers/Quarts)
        return self.highest_card.rank < other.highest_card.rank

    def __gt__(self, other: 'Announcement') -> bool:
        if not isinstance(other, Announcement):
            return NotImplemented

        if self.value != other.value:
            return self.value > other.value

        return self.highest_card.rank > other.highest_card.rank

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Announcement):
            return NotImplemented
        # Announcements are equal if all three properties match
        return (self.type == other.type and
                self.value == other.value and
                self.highest_card.rank == other.highest_card.rank)

    @property
    def value(self):
        return ANNOUNCE_VALUES[self.type]
