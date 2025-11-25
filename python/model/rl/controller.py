import numpy as np

from rl.jass_rules import Card, DIAMOND_SEVEN, Suit, WINNING_SCORE, ANNOUNCE_STOECK
from rl.player import Player
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
        self._trick_count = 0
        self._round_starter = None
        self._last_trick = None

    def reset(self):
        self._round_starter = self._shuffle_deck()
        self._current_player = self._round_starter
        self._trump_selection_first_turn = True
        self._game_over = False
        self._announcements = []
        self._trick = None
        self._last_trick = None
        self._trump_suit = Suit.NONE
        self._trick_count = 0
        self._scores = [0, 0]

    def _new_round(self):
        print(f"new round: score {self._scores}")
        self._shuffle_deck()
        self._trump_selection_first_turn = True
        self._announcements = []
        self._trick = None
        self._last_trick = None
        self._trump_suit = Suit.NONE
        self._trick_count = 0
        self._round_starter = (self._round_starter + 1)  % 4
        self._current_player = self._round_starter

    def play_next_turn(self, forced_action:int=None) -> tuple[int, bool]:
        if self._trump_suit in [Suit.NONE, Suit.PASS]:
            if forced_action is not None:
                assert forced_action < 5
            self._trump_suit = self._players[self._current_player].choose_trump_suit(self._trump_selection_first_turn) if forced_action is None else Suit(forced_action)
            if self._trump_suit == Suit.PASS:
                print(f"Player {self._current_player} passed")
                if not self._trump_selection_first_turn:
                    raise RuntimeError("Second player cannot pass during trump selection")
                self._move_to_partner()
                self._trump_selection_first_turn = False
            else:
                print(f"Player {self._current_player} chose trump suit: {Suit(self._trump_suit)}")
                for p in range(4):
                    pos = self._relative_position(self._current_player, p)
                    self._players[p].set_trump_suit(self._trump_suit, pos, self._trump_selection_first_turn)
                if not self._trump_selection_first_turn:
                    self._move_to_partner()
            return self._trump_suit, False
        move = self._players[self._current_player].choose_card() if forced_action is None else forced_action
        print(f"Player {self._current_player} played {Card(move)}")
        for p in range(4):
            self._players[p].played(Card(move), self._relative_position(self._current_player, p))
        announcements = self._players[self._current_player].get_announcements()
        if announcements:
            self._announcements.extend([(self._current_player, a) for a in announcements])

        self._process_move(Card(move))

        return move, self._game_over

    def get_current_observation(self, player_id) -> np.ndarray:
        assert self._current_player == player_id
        return self._players[player_id].get_state_as_tokens(self._trump_selection_first_turn)

    def get_next_tokens(self, player_id: int) -> tuple[np.ndarray, int, bool]:
        while self._current_player != player_id:
            self.play_next_turn()
            if self._game_over:
                return np.array([]), 0, True
        input_tokens = self._players[player_id].get_state_as_tokens(self._trump_selection_first_turn)
        action, game_over = self.play_next_turn()
        return input_tokens, action, self._game_over

    def _process_move(self, move: Card):
        if self._trick is None:
            self._trick = Trick(self._trump_suit)
        self._trick.play_card(move, self._current_player)
        if self._trick.is_full:
            team_id = self._trick.owner % 2
            self._scores[team_id] += self._trick.score
            print(f"Trick goes to player {self._trick.owner}")
# handle announcements
            if self._announcements:
                self._handle_announcements()
            if any(s >= WINNING_SCORE for s in self._scores):
                self._game_over = True
                return
            self._current_player = self._trick.owner
            self._last_trick = self._trick
            self._trick = None
            self._trick_count += 1
            if self._trick_count == 9:
                self._scores[team_id] += 10 if self._trump_suit == Suit.SPADE else 5
                self._new_round()
                return
        else:
            self._move_to_next_player()

    def _handle_announcements(self):
        highest = None
        player_with_highest = None
        for player, announcement in self._announcements:
            if highest is None or announcement > highest:
                highest = announcement
                player_with_highest = player
            elif announcement.highest_card.suit == self._trump_suit and announcement == highest:
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
            print(f"Announcements: {valid_announcements}")
            for p in range(4):
                localized = [(self._relative_position(pp, p), a) for pp, a in valid_announcements]
                self._players[p].set_announcements(localized)
        self._announcements = []

    def _move_to_next_player(self):
        self._current_player = (self._current_player + 1) % 4

    def _move_to_partner(self):
        self._current_player = (self._current_player + 2) % 4

    def _shuffle_deck(self) -> int:
        deck = np.arange(36)
        np.random.shuffle(deck)
        hands = deck.reshape((4, 9)).astype(np.int64)
        for idx, player in enumerate(self._players):
            player.set_hand([Card(i) for i in hands[idx]])
        idx = np.where(hands == DIAMOND_SEVEN)[0]
        return int(idx[0])

    @staticmethod
    def _relative_position(player, reference):
        return (player + 4 - reference) % 4

    @property
    def current_player(self):
        return self._current_player

    @property
    def scores(self) -> list[int]:
        return self._scores

    @property
    def current_trick(self):
        return self._trick

    @property
    def last_trick(self):
        return self._last_trick
