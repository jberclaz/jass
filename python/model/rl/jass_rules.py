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
from enum import IntEnum
from typing import List, Optional


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
ATOUT = Suit.NONE




@functools.total_ordering
class Card:
    """
    Python representation of a Jass card, wrapping an integer (0-35).
    """

    def __init__(self, number: int):
        if not (0 <= number < DECK_SIZE):
            raise ValueError(f"Invalid card number: {number}")
        self.number = number

    @classmethod
    def from_rank_color(cls, rank: int, color: int) -> 'Card':
        """Creates a card from its rank and color."""
        return cls(color * 9 + rank)

    def get_color(self) -> int:
        return self.number // 9

    def get_rank(self) -> int:
        return self.number % 9

    def get_number(self) -> int:
        return self.number

    def get_value(self) -> int:
        """Gets the point value of the card, respecting the global ATOUT."""
        if self.get_color() == ATOUT:
            return VALUES_ATOUT[self.get_rank()]
        return VALUES[self.get_rank()]

    def _get_compare_rank(self) -> int:
        """Helper for comparison, ranking Bourg and Nell correctly."""
        rank = self.get_rank()
        if self.get_color() == ATOUT:
            if rank == RANK_BOURG:
                return 10  # Highest trump
            if rank == RANK_NELL:
                return 9   # Second highest trump
        return rank # Standard rank 0-8

    def __eq__(self, other):
        if not isinstance(other, Card):
            return NotImplemented
        return self.number == other.number

    def __lt__(self, other):
        """Compares this card to another, *assuming they are the same suit*."""
        if not isinstance(other, Card):
            return NotImplemented
        if self.get_color() != other.get_color():
            raise TypeError("Cannot compare cards of different colors")
        return self._get_compare_rank() < other._get_compare_rank()

    def __hash__(self):
        return hash(self.number)

    def __repr__(self) -> str:
        return f"Card({RANK_NAMES[self.get_rank()]} de {COLOR_NAMES[self.get_color()]})"


class Plie:
    """
    Python representation of a Plie (a trick).
    This class contains the core Jass rules.
    """

    def __init__(self):
        self.highest: Optional[Card] = None  # The card currently winning the trick
        self.cut: bool = False              # Has the trick been trumped?
        self.owner: Optional[PlayerPosition] = None # The player currently winning
        self.cards: List[Card] = []         # Cards played, in order

    def get_lead_color(self) -> int:
        """Returns the color of the first card played, or -1 if empty."""
        return self.cards[0].get_color() if self.cards else -1

    def get_winning_card(self) -> Optional[Card]:
        return self.highest

    def get_owner(self) -> Optional[PlayerPosition]:
        return self.owner

    def get_score(self) -> int:
        """Calculates the total point value of the trick."""
        score = sum(card.get_value() for card in self.cards)
        if ATOUT == Suit.SPADE:
            return score * 2
        return score

    def is_empty(self) -> bool:
        return not self.cards

    def is_full(self) -> bool:
        return len(self.cards) == 4

    def _take_plie(self, card: Card, player: PlayerPosition):
        """Sets the new winning card and owner."""
        self.highest = card
        self.owner = player
        self.cards.append(card)

    def _follow(self, card: Card, player: PlayerPosition):
        """Plays a card that follows the lead suit."""
        if not self.cut and card > self.highest:
            self._take_plie(card, player)
        else:
            self.cards.append(card)

    def _does_not_follow(self, card: Card, player: PlayerPosition, hand: List[Card]):
        """Plays a card that does not follow the lead suit (trump or discard)."""
        if card.get_color() == ATOUT:
            self._cut_plie(card, player, hand)
            return

        # Discarding a non-trump, non-lead-suit card
        if hand:
            has_lead_color = any(c.get_color() == self.get_lead_color() for c in hand)
            if has_lead_color:
                # Simplified rule: Java checks for "Bourg Sec", we'll just error
                if self.get_lead_color() != ATOUT:
                    raise RuntimeError("Must follow suit")

        self.cards.append(card)

    def _cut_plie(self, card: Card, player: PlayerPosition, hand: List[Card]):
        """Plays a trump card."""
        if not self.cut:
            self._take_plie(card, player)
            self.cut = True
            return

        if card > self.highest:
            self._take_plie(card, player)
            return

        # Undercutting logic
        if hand:
            has_non_trump = any(c.get_color() != ATOUT for c in hand)
            if has_non_trump:
                raise RuntimeError("Cannot under-trump if non-trump cards are held")

            has_higher_trump = any(c > self.highest for c in hand if c.get_color() == ATOUT)
            if has_higher_trump:
                raise RuntimeError("Cannot under-trump if a higher trump is held")

        self.cards.append(card)

    def play_card(self, card: Card, player: PlayerPosition, hand: List[Card]):
        """
        Plays a card into the trick, applying all Jass rules.

        Args:
            card: The Card object being played.
            player: The BasePlayer object playing the card.
            hand: The player's *entire* current hand (as a list of Cards)
                  to check for broken rules.

        Raises:
            BrokenRuleException: If the play is illegal (e.g., must-follow).
        """
        if self.is_full():
            raise RuntimeError("Trick is already full")

        if self.is_empty():
            self._take_plie(card, player)
        elif card.get_color() == self.get_lead_color():
            self._follow(card, player)
        else:
            self._does_not_follow(card, player, hand)

    def can_play(self, card: Card, hand: List[Card]) -> bool:
        """
        Checks if a card is a legal play *without* throwing exceptions.
        This is the "legal mask" logic.
        """
        if self.is_empty():
            return True  # Can play anything

        lead_color = self.get_lead_color()

        if card.get_color() == lead_color:
            return True  # Following suit is always legal

        # Not following suit...
        has_lead_color = any(c.get_color() == lead_color for c in hand)

        if card.get_color() == ATOUT:
            if has_lead_color:
                # You have the lead color, but you're trumping.
                # This is only legal if the lead color *is* trump,
                # which we already checked (and it failed).
                # Simplified: No "Bourg Sec" check.
                return False # Must follow suit

            # Don't have lead color, playing trump.
            if not self.cut:
                return True  # First trump is always legal

            if card > self.highest:
                return True  # Over-trumping is legal

            # Undercutting. Is it legal?
            has_non_trump = any(c.get_color() != ATOUT for c in hand)
            if has_non_trump:
                return False  # Illegal: must discard non-trump

            has_higher_trump = any(c > self.highest for c in hand if c.get_color() == ATOUT)
            return not has_higher_trump  # Legal only if no higher trump

        # Discarding (not lead suit, not trump)
        if has_lead_color:
            return False  # Illegal: must follow suit

        return True # Legal to discard
