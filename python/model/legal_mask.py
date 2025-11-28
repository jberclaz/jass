import torch

from rl.jass_rules import TRUMP_RANK_MAP, TRUMP_RANK_LIST, RANK_BOURG


def get_legal_mask(hand_tokens):
    # Extract hand cards (positions 10-18)
    hand = hand_tokens[10:19]
    valid = hand != 0
    card_ids = (hand[valid] - 10).clamp(0, 35)
    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    return mask


def get_legal_mask_with_rules(tokens: torch.Tensor) -> torch.Tensor:
    phase = tokens[2]
    if phase == 126:
        return torch.zeros(36, dtype=torch.bool)
    trump = tokens[5] - 56
    hand_tokens = tokens[11:20]
    valid = hand_tokens != 0
    card_ids = hand_tokens[valid] - 10

    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    if tokens[22] == 0:
        # first card to play, anything is legal
        return mask

    leading_trick_suit = (tokens[22] - 10) // 9
    cut = False
    highest_cut_rank = -1
    if leading_trick_suit != trump:
        for t in (24, 26):
            if tokens[t] == 0:
                break
            card_suit = (tokens[t] - 10) // 9
            if card_suit == trump:
                cut = True
                rank = TRUMP_RANK_MAP[(int(tokens[t]) - 10) % 9]
                if rank > highest_cut_rank:
                    highest_cut_rank = rank

    has_suit = any((c // 9) == leading_trick_suit for c in card_ids)
    bourg_sec = None
    for card_id in card_ids:
        suit = card_id // 9
        if suit == leading_trick_suit:
            continue
        if suit == trump:
            if not cut:
                continue
            rank = TRUMP_RANK_MAP[int(card_id) % 9]
            if rank > highest_cut_rank:
                continue
            has_non_trump_cards = any((c // 9) != trump for c in card_ids)
            if not has_non_trump_cards:
                has_higher_trump = any((c % 9) > highest_cut_rank for c in card_ids)
                if not has_higher_trump:
                    continue
        elif not has_suit:
            continue
        elif leading_trick_suit == trump:
            if bourg_sec is None:
                trump_cards = [c for c in card_ids if c // 9 == trump]
                bourg_sec = len(trump_cards) == 1 and trump_cards[0] % 9 == RANK_BOURG
            if bourg_sec:
                continue
        mask[card_id] = False
    return mask

def get_legal_mask_with_rules_batch(tokens: torch.Tensor) -> torch.Tensor:
    """
    Calculates the legal card mask for a batch of game states using tensor operations,
    accounting for correct Trump rank ordering (Jack > 9) and the 'Bourg Sec' rule.

    Args:
        tokens: A (B, L) tensor, where B is batch_size and L is token_length.

    Returns:
        A (B, 36) boolean tensor, where True indicates a legal card to play.
    """
    B = tokens.shape[0]
    device = tokens.device

    # Constants
    # 5 is the standard raw index for Jack (6,7,8,9,10,J,Q,K,A -> 0,1,2,3,4,5,6,7,8)
    RAW_RANK_JACK = 5
    TRUMP_RANK_LIST = [0, 1, 2, 7, 3, 8, 4, 5, 6] # Example mapping, ensure this matches your global constant

    # Initialize the rank lookup tensor
    rank_lookup = torch.tensor(TRUMP_RANK_LIST, device=device, dtype=torch.long)

    is_card_play_phase = (tokens[:, 2] == 125).unsqueeze(1)

    # 1. Get trump suit (B, 1)
    trump = tokens[:, 5].sub(56).unsqueeze(1)

    # 2. Get hand cards (B, 9)
    hand_tokens = tokens[:, 11:20]
    valid = hand_tokens != 0
    card_ids = hand_tokens.sub(10)

    # 3. Create base mask of all cards in hand
    base_hand_mask_float = torch.zeros((B, 36), dtype=torch.float32, device=device)
    index = card_ids.clamp(min=0)
    src = valid.float()
    base_hand_mask_float.scatter_add_(dim=1, index=index, src=src)
    base_hand_mask = base_hand_mask_float > 0

    # 4. Check if first player to play
    is_first_player = (tokens[:, 22] == 0).unsqueeze(1)

    # --- Logic for non-first players ---

    # 5. Get trick state
    leading_trick_suit = (tokens[:, 22] - 10).div(9, rounding_mode='floor').unsqueeze(1)

    # Shape: (B, 2)
    trick_cards = tokens[:, [24, 26]]
    trick_valid = trick_cards != 0
    trick_card_ids = trick_cards.sub(10)

    trick_suits = trick_card_ids.div(9, rounding_mode='floor')
    trick_raw_ranks = trick_card_ids % 9

    # Apply Rank Map: Map raw indices to Trump Strength
    trick_mapped_ranks = rank_lookup[trick_raw_ranks.clamp(min=0)]

    # Check which trick cards are valid TRUMP cards
    is_valid_trump = (trick_suits == trump) & trick_valid

    # Was a cut (trump) played?
    cut = is_valid_trump.any(dim=1).unsqueeze(1)

    # Get ranks of only the valid trump cards, else -1
    valid_trump_ranks = torch.full_like(trick_mapped_ranks, -1)
    valid_trump_ranks = torch.where(is_valid_trump, trick_mapped_ranks, valid_trump_ranks)

    # Get the highest trump rank played so far
    highest_cut_rank = valid_trump_ranks.max(dim=1).values.unsqueeze(1)

    # 6. Get hand properties
    hand_suits = card_ids.div(9, rounding_mode='floor')
    hand_raw_ranks = card_ids % 9

    # Map hand cards to Trump Strength
    hand_mapped_ranks = rank_lookup[hand_raw_ranks.clamp(min=0)]

    # Basic hand checks
    has_suit = ((hand_suits == leading_trick_suit) & valid).any(dim=1).unsqueeze(1)
    has_non_trump_cards = ((hand_suits != trump) & valid).any(dim=1).unsqueeze(1)
    has_higher_trump = ((hand_mapped_ranks > highest_cut_rank) & (hand_suits == trump) & valid).any(dim=1).unsqueeze(1)

    # --- BOURG SEC LOGIC START ---

    # Is the lead suit Trump?
    lead_is_trump = (leading_trick_suit == trump)

    # Identify Trumps held in hand
    hand_trump_mask = (hand_suits == trump) & valid

    # Count Trumps in hand
    trump_counts = hand_trump_mask.sum(dim=1, keepdim=True)

    # Does hand contain the Jack of Trumps?
    has_jack = ((hand_raw_ranks == RAW_RANK_JACK) & hand_trump_mask).any(dim=1, keepdim=True)

    # Condition: Lead is Trump AND We have exactly 1 Trump AND that Trump is the Jack
    is_bourg_sec = lead_is_trump & (trump_counts == 1) & has_jack

    # --- BOURG SEC LOGIC END ---

    # 7. Compute legality for each card in hand
    is_lead_suit = (hand_suits == leading_trick_suit)
    is_trump_suit = (hand_suits == trump)

    # 1. Follow suit
    legal_cond1 = is_lead_suit

    # 2. Play trump (no trump played yet in trick)
    legal_cond2 = is_trump_suit & ~cut

    # 3. Play trump (must beat highest trump played)
    legal_cond3 = is_trump_suit & cut & (hand_mapped_ranks > highest_cut_rank)

    # 4. Under-trump (only allowed if hand is all trump and cannot go higher)
    legal_cond4 = is_trump_suit & cut & (hand_mapped_ranks <= highest_cut_rank) & \
                  ~has_non_trump_cards & ~has_higher_trump

    # 5. Discard (no lead suit, no trump, or forced to discard non-trump)
    # Note: If Lead==Trump, 'has_suit' is True. This cond fails.
    legal_cond5 = ~is_lead_suit & ~is_trump_suit & ~has_suit

    # 6. Bourg Sec Exception
    # If Lead==Trump and Bourg Sec, you may play non-trump cards (renege).
    legal_cond6 = is_bourg_sec & ~is_trump_suit

    # Combine all legal conditions
    is_legal_card = legal_cond1 | legal_cond2 | legal_cond3 | legal_cond4 | legal_cond5 | legal_cond6

    # 8. Create the complex legal mask
    legal_cards_in_hand = valid & is_legal_card

    complex_mask_float = torch.zeros((B, 36), dtype=torch.float32, device=device)
    src = legal_cards_in_hand.float()
    complex_mask_float.scatter_add_(dim=1, index=index, src=src)
    complex_legal_mask = complex_mask_float > 0

    # 9. Final selection
    card_play_mask = torch.where(is_first_player, base_hand_mask, complex_legal_mask)
    all_false_mask = torch.zeros((B, 36), dtype=torch.bool, device=device)
    final_mask = torch.where(is_card_play_phase, card_play_mask, all_false_mask)

    return final_mask


def get_trump_mask_batch(tokens: torch.Tensor) -> torch.Tensor:
    """
    Calculates the legal trump choice mask for a batch of game states.
    Output shape is (B, 5).

    This mask is all False if in "card play" phase (token[2] == 125).
    If in "trump choice" phase (token[2] == 126):
        - If token[3] == 50 (first turn): Returns all True [T, T, T, T, T]
        - If token[3] == 51 (second turn): Returns [T, T, T, T, F] (cannot pass)

    Args:
        tokens: A (B, L) tensor, where B is batch_size and L is token_length.

    Returns:
        A (B, 5) boolean tensor, where True indicates a legal trump choice.
    """
    B = tokens.shape[0]
    device = tokens.device

    # Get relevant tokens
    # Shape: (B,)
    phase = tokens[:, 2]
    trump_turn = tokens[:, 3]

    # --- Define possible masks ---

    # 1. Mask for Phase 1 (Card Play): All False
    # Shape: (B, 5)
    all_false_mask = torch.zeros((B, 5), dtype=torch.bool, device=device)

    # 2. Mask for Phase 2 (Trump Choice), Turn 1 (token[3] == 50): All True
    # Shape: (B, 5)
    all_true_mask = torch.ones((B, 5), dtype=torch.bool, device=device)

    # 3. Mask for Phase 2 (Trump Choice), Turn 2 (token[3] == 51): [T, T, T, T, F]
    # Assumes the 5th option is "pass"
    # Shape: (B, 5)
    no_pass_mask = torch.tensor([True, True, True, True, False],
                                dtype=torch.bool, device=device).expand(B, -1)

    # --- Select the correct mask based on conditions ---

    # Conditions (broadcastable to (B, 1))
    is_phase_2 = (phase == 126).unsqueeze(1)
    is_turn_1 = (trump_turn == 50).unsqueeze(1)

    # 1. First, determine the correct mask *if* we are in Phase 2
    # If it's turn 1, use all_true. Otherwise (assume turn 2), use no_pass.
    phase_2_mask = torch.where(is_turn_1, all_true_mask, no_pass_mask)

    # 2. Now, choose between the Phase 2 mask and the Phase 1 (all_false) mask
    final_mask = torch.where(is_phase_2, phase_2_mask, all_false_mask)

    return final_mask