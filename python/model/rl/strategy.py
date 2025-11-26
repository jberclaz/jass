import random

from rl.jass_rules import Card, Suit
from rl.trick import Trick


class Strategy:
    def __init__(self):
        self._player = None

    def __str__(self):
        return "Base"

    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        pass

    def choose_trump_suit(self, hand: list[Card], first: bool):
        pass

    def set_player(self, player: 'Player'):
        self._player = player

    def update_model(self, state_dict):
        pass


class RandomStrategy(Strategy):
    def __init__(self):
        super().__init__()

    def __str__(self):
        return "random"

    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        return random.choice(legal_moves).number

    def choose_trump_suit(self, hand: list[Card], first: bool):
        return random.randint(0, 4 if first else 3)


class HeuristicStrategy(Strategy):
    def __init__(self):
        super().__init__()

    def __str__(self):
        return "heuristic"

    def choose_trump_suit(self, hand: list[Card], first: bool):
        # Group by suit and count
        counts = {}
        for card in hand:
            counts[card.suit] = counts.get(card.suit, 0) + 1

        best_suit = -1
        highest_count = 0

        # Find suit with most cards
        # In Python 3.7+, dict order is insertion order, so iteration is stable
        for suit, count in counts.items():
            if count > highest_count:
                best_suit = suit
                highest_count = count

        # If hand is empty or something weird, fallback to 0 or random
        if best_suit == -1:
            return Suit.PASS

        return best_suit

    def choose_card(self, legal_moves: list[Card], hand: list[Card]):
        # We need access to the current trick (plie) to make decisions.
        # Assuming self._player has a reference to the game state or controller.
        # Let's assume self._player.current_trick returns the list of played cards in current trick
        trump_suit = self._player._current_trump
        current_trick = self._player._trick

        # Sort moves by rank (power) ascending
        # Assuming card.rank is the power value
        sorted_moves = sorted(legal_moves, key=lambda c: c.rank)

        # Rule 2: Leading a new trick (Trick is empty)
        if current_trick is None or current_trick.is_empty:
            # Try to lead with highest-power NON-TRUMP card
            best_non_trump_lead = None

            # Iterate low-to-high
            for card in sorted_moves:
                if card.suit != trump_suit:  # Assuming Card.atout constant exists for Trump suit ID
                    best_non_trump_lead = card

            if best_non_trump_lead is not None:
                return best_non_trump_lead.number  # Return card ID

            # If only trumps left, lead with lowest trump (safe play)
            return sorted_moves[0].number

        # Rule 3: Following a trick
        winning_card = current_trick.get_winning_card()

        # Check if partner is winning
        # Assuming standard 4 player game:
        # If trick size is 2, partner played 1st.
        # If trick size is 3, partner played 2nd.
        # We need to know WHO played which card.
        # Let's assume current_trick is list of (player_id, card) or we can deduce index.

        # Simplification based on Java logic:
        # "partnerIsWinning = plie.getSize() > 1 && ((plie.getSize() - plie.getWinningIndex()) % 2 == 0);"
        # This implies we check if the winning card index relative to us is the partner position.

        partner_is_winning = self.is_partner_winning(current_trick)

        if partner_is_winning:
            # --- LOGIC: PARTNER IS WINNING ---
            # Goal: "Schmieren" (give points) if possible, but don't take the trick.

            best_to_schmier = None
            # Iterate high-to-low power to find biggest point card that DOESN'T win
            for card in reversed(sorted_moves):
                if not self.is_winning(card, winning_card, trump_suit):
                    # We want to maximize points (value), not necessarily rank
                    if best_to_schmier is None or card.get_value(trump_suit) > best_to_schmier.get_value(trump_suit):
                        best_to_schmier = card

            if best_to_schmier:
                return best_to_schmier.number

            # If all valid moves win (e.g. forced to trump), play lowest power to steal cheaply
            return sorted_moves[0].number

        else:
            # --- LOGIC: OPPONENT IS WINNING (or we are) ---
            # Goal: Win as cheaply as possible.

            # 1. Find cheapest card that wins
            for card in sorted_moves:
                if self.is_winning(card, winning_card, trump_suit):
                    return card.number

            # 2. Can't win. Lose as cheaply as possible (lowest value/points).
            cheapest_loss = sorted_moves[0]
            for card in sorted_moves:
                if card.get_value(trump_suit) < cheapest_loss.get_value(trump_suit):
                    cheapest_loss = card

            return cheapest_loss.number

    @staticmethod
    def is_partner_winning(trick):
        # You'll need to implement this based on how your Python 'trick' object works.
        # If trick is list of cards played in order:
        # If len(trick) == 2: Partner played 1st (index 0). If index 0 is winning, True.
        # If len(trick) == 3: Partner played 2nd (index 1). If index 1 is winning, True.
        if not trick:
            return False

        winning_idx = trick.get_winning_index()
        current_size = trick.count

        # Partner is always 2 seats away.
        # If I am player 3 (about to play 4th card), partner is player 1 (2nd card).
        # Relative distance logic:
        return (current_size - winning_idx) % 2 == 0


    @staticmethod
    def is_winning(new_card: Card, old_card: Card, trump_suit):
        """Helper to check if new_card beats old_card based on Jass rules"""
        if old_card is None:
            return True

        # 1. New is trump, Old is not
        if new_card.suit == trump_suit and old_card.suit != trump_suit:
            return True

        # 2. Old is trump, New is not
        if new_card.suit != trump_suit and old_card.suit == trump_suit:
            return False

        # 3. Same suit
        if new_card.suit == old_card.suit:
            # Assuming rank is ordered such that higher is better
            # OR you need a specific Jass rank comparison
            return new_card.rank > old_card.rank

        # 4. Different suits, neither is trump
        # New card loses because it didn't follow suit of the first card (old_card)
        # (Assuming old_card is the current winner, which usually follows lead suit)
        return False