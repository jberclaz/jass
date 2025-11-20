from rl.jass_rules import Card, DIAMOND_SEVEN, Suit, WINNING_SCORE, ANNOUNCE_STOECK
from rl.player import Player
import numpy as np

from rl.trick import Trick


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
        self._game_over = False

    def reset(self):
        deck = np.arange(36)
        np.random.shuffle(deck)
        hands = deck.reshape((4, 9)).astype(np.int64)
        for idx, player in enumerate(self._players):
            player.set_hand([Card(i) for i in hands[idx]])
        self._current_player = int(np.where(hands == DIAMOND_SEVEN)[0])
        self._trump_selection_first_turn = True
        self._game_over = False
        self._announcements = []
        self._trick = None
        self._scores = [0, 0]
        self._trump_suit = Suit.NONE

    def play_next_turn(self) -> tuple[int, bool]:
        if self._trump_suit == Suit.NONE:
            self._trump_suit = self._players[self._current_player].choose_trump_suit(self._trump_selection_first_turn)
            if self._trump_suit == Suit.NONE:
                if not self._trump_selection_first_turn:
                    raise RuntimeError("Second player cannot pass during trump selection")
                self._move_to_partner()
                self._trump_selection_first_turn = False
            else:
                for p in self._players:
                    p.set_trump_suit(self._trump_suit)
                if not self._trump_selection_first_turn:
                    self._move_to_partner()
            return self._trump_suit, False
        move = self._players[self._current_player].choose_card()
        for p in range(4):
            self._players[p].played(Card(move), self._relative_position(self._current_player, p))
        announcements = self._players[self._current_player].get_announcements()
        if announcements:
            self._announcements.extend([(self._current_player, a) for a in announcements])

        self._process_move(Card(move))

        self._move_to_next_player()
        return move, self._game_over

    def get_next_tokens(self, player_id: int) -> tuple[list[int], int, bool]:
        while self._current_player != player_id:
            self.play_next_turn()
            if self._game_over:
                return [], 0, True
        input_tokens = self._players[player_id].get_state_as_tokens()
        action, game_over = self.play_next_turn()
        return input_tokens, action, self._game_over

    def _process_move(self, move: Card):
        if self._trick is None:
            self._trick = Trick(self._trump_suit)
        self._trick.play_card(move, self._current_player)
        if self._trick.is_full:
            team_id = self._trick._owner % 2
            self._scores[team_id] += self._trick.score
            # handle announcements
            if self._announcements:
                self._handle_announcements()
            if any(s >= WINNING_SCORE for s in self._scores):
                self._game_over = True
            self._trick = None

    def _handle_announcements(self):
        highest = None
        player_with_highest = None
        for player, announcement in self._announcements:
            if highest is None or announcement > highest:
                highest = announcement
                player_with_highest = player
            elif announcement.highest_card.suit() == self._trump_suit and announcement == highest:
                highest = announcement
                player_with_highest = player
        announcement_team_id = player_with_highest % 2
        valid_announcements = []
        for player, announcement in self._announcements:
            if player % 2 == announcement_team_id or announcement.type == ANNOUNCE_STOECK:
                value = announcement.value * 2 if self._trump_suit == Suit.SPADE else announcement.value
                self._scores[player % 2] += value
                valid_announcements.append((player, announcement))
        if valid_announcements:
            for p in self._players:
                p.set_announcements(valid_announcements)

    def _move_to_next_player(self):
        self._current_player = (self._current_player + 1) % 4

    def _move_to_partner(self):
        self._current_player = (self._current_player + 2) % 4

    @staticmethod
    def _relative_position(player, reference):
        return (player + 4 - reference) % 4
