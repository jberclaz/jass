from enum import IntEnum

DIAMOND_SEVEN = 3 * 9 + 1

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