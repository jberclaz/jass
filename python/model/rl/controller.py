from rl.jass_rules import Card, DIAMOND_SEVEN, Suit
from rl.player import Player
import numpy as np

class Controller:
    def __init__(self, players: list[Player]):
        self._players = players
        assert len(players) == 4
        self._current_player = None
        self._trump_suit = Suit.NONE
        self._scores = [0, 0]
        self._trick = None
        self._trump_selection_first_turn = True
        self._announcements = []

    def reset(self):
        deck = np.arange(36)
        np.random.shuffle(deck)
        hands = deck.reshape((4, 9)).astype(np.int64)
        for idx, player in enumerate(self._players):
            player.set_hand([Card(i) for i in hands[idx]])
        self._current_player = np.where(hands == DIAMOND_SEVEN)[0]
        self._trump_selection_first_turn = True

    def play_next_turn(self, player_id = None, return_data: bool = False):
        if self._trump_suit == Suit.NONE:
            self._trump_suit = self._players[self._current_player].choose_trump_suit(self._trump_selection_first_turn)
            if self._trump_suit == Suit.NONE:
                self._move_to_partner()
                self._trump_selection_first_turn = False
            if not self._trump_selection_first_turn:
                self._move_to_partner()
            return
        move = self._players[self._current_player].choose_card()
        for p in range(4):
            self._players[p].played(move, self._relative_position(self._current_player, p))
        self._announcements.extend(self._players[self._current_player].get_announcements())

        self._move_to_next_player()
        return

    def _move_to_next_player(self):
        self._current_player = (self._current_player + 1) % 4

    def _move_to_partner(self):
        self._current_player = (self._current_player + 2) % 4

    @staticmethod
    def _relative_position(player, reference):
        return (player + 4 - reference) % 4