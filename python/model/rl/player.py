from rl.tokens import Tokens
from rl.trick import Trick
from rl.jass_rules import Card, Suit, PlayerPosition, Announcement, RANK_DAME, RANK_ROI, ANNOUNCE_STOECK
from rl.strategy import Strategy
from dataset import TOKEN_LENGTH
import numpy as np

class Player:
    def __init__(self, strategy: Strategy):
        self._strategy = strategy
        self._current_trump = Suit.NONE
        self._hand = []
        self._trick = None
        self._past_tricks = []
        self._scores = [0, 0]
        self._game_scores = [0, 0]
        self._opponent_score = 0
        self._announcements = []
        self._has_stoeck = False
        self._trump_selector = None
        self._trump_chosen_on_first_turn = False
        self._known_cards_in_hand = None
        self._deck_probs = None

    def set_hand(self, hand: list[Card]):
        self._hand = hand
        self._current_trump = Suit.NONE
        self._deck_probs = np.full((36, 3), 1/3, dtype=np.float32)
        my_hand = np.array([c.number for c in hand])
        self._deck_probs[my_hand,:] = 0
        self._known_cards_in_hand = [[], [], []]
        self._past_tricks = []

    def set_trump_suit(self, suit: Suit, player: PlayerPosition, chosen_on_first_turn: bool):
        self._current_trump = suit
        self._trump_selector = player
        self._trump_chosen_on_first_turn = chosen_on_first_turn
        self._compute_announcements()

    def set_announcements(self, announcements):
        for player, a in announcements:
            self._scores[player % 2] += 2 * a.value if self._current_trump == Suit.SPADE else a.value

    def get_announcements(self):
        if len(self._hand) == 8:  # after playing the first card
            return self._announcements
        if self._has_stoeck:
            stoeck = (Card.from_rank_color(RANK_DAME, self._current_trump),
                      Card.from_rank_color(RANK_ROI, self._current_trump))
            if sum(c in stoeck for c in self._hand) == 0:
                self._has_stoeck = False
                return [Announcement(ANNOUNCE_STOECK, stoeck[1])]
        return []

    def played(self, card: Card, player: PlayerPosition):
        if self._trick is None:
            self._trick = Trick(self._current_trump)
        self._trick.play_card(card, player)

        if player == PlayerPosition.SELF:
            self._hand.remove(card)

        if self._trick.is_full:
            team_id = self._trick.owner % 2
            self._game_scores[team_id] += self._trick.score
            self._past_tricks.append(self._trick)
            self._trick = None

    def choose_trump_suit(self, first: bool) -> Suit:
        suit_number = self._strategy.choose_trump_suit(self._hand, first)
        return Suit(suit_number)

    def choose_card(self) -> int:
        if self._trick is None:
            self._trick = Trick(self._current_trump)
        legal_moves = self._get_legal_moves()
        return self._strategy.choose_card(legal_moves, self._hand)

    def get_state_as_tokens(self, first_turn_of_trump_selection: bool) -> list[int]:
        tokens = np.zeros(TOKEN_LENGTH, dtype=np.int64)
        tokens.fill(Tokens.PAD)

        # === 0: CLS ===
        tokens[0] = Tokens.CLS

        # === 1-9: GLOBALS (9 tokens) ===
        tokens[1] = Tokens.SECTION_GLOBALS

        # Get relative scores
        our_game_score = self._game_scores[0]
        opp_game_score = self._game_scores[1]
        our_match_score = self._scores[0]
        opp_match_score = self._scores[1]

        if self._current_trump == Suit.NONE:
            # --- Trump Choice Phase Globals ---
            tokens[2] = Tokens.CHOOSE_TRUMP_SUIT
            tokens[3] = Tokens.TRUMP_FIRST_CHOICE if first_turn_of_trump_selection else Tokens.TRUMP_FORCED
            tokens[4] = Tokens.position_token(0)  # Position is "SELF"
            # 5 is already padded
        else:
            # --- Card Play Phase Globals ---
            tokens[2] = Tokens.CHOOSE_NEXT_CARD
            tokens[3] = Tokens.TRUMP_FIRST_CHOICE if self._trump_chosen_on_first_turn else Tokens.TRUMP_FORCED
            tokens[4] = Tokens.position_token(self._trump_selector)
            tokens[5] = Tokens.trump_token(self._current_trump)

        tokens[6] = Tokens.game_score_token(our_game_score)
        tokens[7] = Tokens.game_score_token(opp_game_score)
        tokens[8] = Tokens.match_score_token(our_match_score)
        tokens[9] = Tokens.match_score_token(opp_match_score)

        # === 10-19: HAND (10 tokens) ===
        tokens[10] = Tokens.SECTION_HAND

        # Get and sort the current player's hand
        hand = [c.number for c in self._hand]
        for i in range(9):
            if i < len(hand):
                tokens[11 + i] = Tokens.card_token(hand[i])
            # otherwise: already padded

        # === 20-26: CURRENT TRICK (7 tokens) ===
        tokens[20] = Tokens.SECTION_TRICK

        if self._current_trump == Suit.NONE:
            tokens[27] = Tokens.SECTION_HISTORY
            tokens[68] = Tokens.SECTION_BELIEF
            return tokens.tolist()

        token_idx = 21
        if self._trick is not None:
            for idx, card in enumerate(self._trick.cards):
                if token_idx < 27:
                    position = 4 - self._trick.count + idx
                    tokens[token_idx] = Tokens.position_token(position)
                    tokens[token_idx + 1] = Tokens.card_token(card.number)
                    token_idx += 2
            # (Rest are already padded)

        # === 27-67: HISTORY (41 tokens) ===
        # (8 tricks * 5 tokens/trick + 1 section token)
        tokens[27] = Tokens.SECTION_HISTORY

        token_idx = 28
        # Iterate history, most recent trick first (Java `lastCompletedTricks.add`)
        for trick in self._past_tricks:
            for card in trick.cards:
                tokens[token_idx] = Tokens.card_token(card.number)
                token_idx += 1

            tokens[token_idx] = Tokens.WIN_OUR_TEAM if trick.owner % 2 == 0 else Tokens.WIN_OPP_TEAM
            token_idx += 1

        # === 68-95: BELIEF / KNOWN HANDS (28 tokens) ===
        # (1 section + 3 opponents * (1 pos + 4 * 2 card/conf))
        tokens[68] = Tokens.SECTION_BELIEF

        token_idx = 69
        # Loop through opponents (e.g., Left, Partner, Right)
        max_cards_per_player = 4
        for p in range(3):
            pos = p + 1
            # Add opponent position token
            tokens[token_idx] = Tokens.position_token(pos)
            token_idx += 1

            known_cards_count = min(max_cards_per_player, len(self._known_cards_in_hand[p]))
            for i in range(known_cards_count):
                tokens[token_idx] = Tokens.card_token(self._known_cards_in_hand[p][i])
                tokens[token_idx + 1] = Tokens.confidence_token(1.0)
                token_idx += 2

            remaining_cards_count = max_cards_per_player - known_cards_count
            if remaining_cards_count > 0:
                card_ids, probs = self._get_k_most_likely_cards(p, remaining_cards_count)
                for i in range(len(card_ids)):
                    tokens[token_idx] = Tokens.card_token(card_ids[i])
                    tokens[token_idx + 1] = Tokens.confidence_token(probs[i])
                    token_idx += 2
                remaining_cards_count = max_cards_per_player - known_cards_count - len(card_ids)

            if remaining_cards_count > 0:
                for i in range(remaining_cards_count):
                    token_idx += 2
                    # tokens already padded

        # (Rest are already padded)

        assert token_idx == 96, f"Token generation ended at index {token_idx}, expected 96"

        return tokens.tolist()

    def _get_k_most_likely_cards(self, player_idx: int, k: int, threshold: float =0.34) -> tuple[list[int], list[float]]:
        # 1. Select the player's probability column
        player_probs = self._deck_probs[:, player_idx]

        # 2. Boolean Masking: Get the indices (Card IDs) of all candidates above the threshold
        candidate_card_ids = np.where(player_probs > threshold)[0]
        candidate_probs = player_probs[candidate_card_ids]

        # 3. Argsort: Find the indices that sort the candidate probabilities
        sorted_candidate_indices = np.argsort(candidate_probs)

        # 4. Determine how many to select (min of k or available candidates)
        num_to_select = min(k, len(candidate_card_ids))

        # 5. Final Selection: Map the top indices back to the original Card IDs and get probabilities
        top_k_indices_in_candidates = sorted_candidate_indices[-num_to_select:][::-1]

        # FINAL RESULTS ARRAYS:
        return candidate_card_ids[top_k_indices_in_candidates], candidate_probs[top_k_indices_in_candidates]

    def _compute_announcements(self):
        self._announcements = Announcement.find_announcements(self._hand)
        stoeck = (Card.from_rank_color(RANK_DAME, self._current_trump),
                  Card.from_rank_color(RANK_ROI, self._current_trump))
        self._has_stoeck = sum(c in stoeck for c in self._hand) == 2

    def _get_legal_moves(self):
        legal = []
        for card in self._hand:
            if self._trick.can_play(card, self._hand):
                legal.append(card)
        return legal
