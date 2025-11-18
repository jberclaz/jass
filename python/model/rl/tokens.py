from enum import IntEnum

class Tokens(IntEnum):
    PAD = 0
    CLS = 1
    SEP = 2

    SECTION_GLOBALS = 3
    SECTION_HAND = 4
    SECTION_TRICK = 5
    SECTION_HISTORY = 6
    SECTION_BELIEF = 7

    TRUMP_FIRST_CHOICE = 50
    TRUMP_FORCED = 51

    P_SELF = 52
    P_PARTNER = 53
    P_OPP_L = 54
    P_OPP_R = 55

    WIN_OUR_TEAM = 123
    WIN_OPP_TEAM = 124

    CHOOSE_NEXT_CARD = 125
    CHOOSE_TRUMP_SUIT = 126

    @staticmethod
    def trump_token(t):
        return 56 + t

    @staticmethod
    def position_token(p):
        if p == 0:
            return Tokens.P_SELF
        if p == 1:
            return Tokens.P_OPP_R
        if p == 2:
            return Tokens.P_PARTNER
        if p == 3:
            return Tokens.P_OPP_L
        raise RuntimeError(f"Unexpected position {p}")

    @staticmethod
    def game_score_token(score):
        return 60 + min(score // 10, 25)

    @staticmethod
    def match_score_token(score):
        return 86 + min(score / 100, 25)

    @staticmethod
    def card_token(card: int):
        return 10 + card