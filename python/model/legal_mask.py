import torch


def get_legal_mask(hand_tokens):
    # Extract hand cards (positions 10-18)
    hand = hand_tokens[10:19]
    valid = hand != 0
    card_ids = (hand[valid] - 10).clamp(0, 35)
    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    return mask


def get_legal_mask_with_rules(tokens: torch.Tensor) -> torch.Tensor:
    trump = tokens[4] - 56
    hand_tokens = tokens[10:19]
    valid = hand_tokens != 0
    card_ids = hand_tokens[valid] - 10

    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    if tokens[21] == 0:
        # first card to play, anything is legal
        return mask

    leading_trick_suit = (tokens[21] - 10) // 9
    cut = False
    highest_cut_rank = -1
    for t in (23, 25):
        if tokens[t] == 0:
            break
        card_suit = (tokens[t] - 10) // 9
        if card_suit == trump:
            cut = True
            rank = (tokens[t] - 10) % 9
            if rank > highest_cut_rank:
                highest_cut_rank = rank

    has_suit = any((c // 9) == leading_trick_suit for c in card_ids)

    for card_id in card_ids:
        suit = card_id // 9
        if suit == leading_trick_suit:
            continue
        if suit == trump:
            if not cut:
                continue
            rank = card_id % 9
            if rank > highest_cut_rank:
                continue
            has_non_trump_cards = any((c // 9) != trump for c in card_ids)
            if not has_non_trump_cards:
                has_higher_trump = any((c % 9) > highest_cut_rank for c in card_ids)
                if not has_higher_trump:
                    continue
        elif not has_suit:
            continue
        mask[card_id] = False
    return mask



def get_legal_mask_with_rules_batch(tokens: torch.Tensor) -> torch.Tensor:
    """
    Calculates the legal card mask for a batch of game states using tensor operations.

    Args:
        tokens: A (B, L) tensor, where B is batch_size and L is token_length.

    Returns:
        A (B, 36) boolean tensor, where True indicates a legal card to play.
    """
    B = tokens.shape[0]
    device = tokens.device

    # 1. Get trump suit
    # Shape: (B, 1) for broadcasting
    trump = tokens[:, 4].sub(56).unsqueeze(1)

    # 2. Get hand cards
    # Shape: (B, 9)
    hand_tokens = tokens[:, 10:19]
    valid = hand_tokens != 0
    # card_ids can be -10 for invalid/empty slots
    card_ids = hand_tokens.sub(10)

    # 3. Create base mask of all cards in hand
    # This mask is returned if it's the first player to play.
    # Shape: (B, 36)
    base_hand_mask_float = torch.zeros((B, 36), dtype=torch.float32, device=device)

    # We use clamp(min=0) to safely index. Invalid cards (id -10)
    # will be clamped to 0, but their 'src' will be 0.0 (from valid.float()),
    # so they won't incorrectly mark card 0 as playable.
    index = card_ids.clamp(min=0)
    src = valid.float()
    base_hand_mask_float.scatter_add_(dim=1, index=index, src=src)
    base_hand_mask = base_hand_mask_float > 0

    # 4. Check if first player to play
    # Shape: (B, 1)
    is_first_player = (tokens[:, 21] == 0).unsqueeze(1)

    # --- All logic below is for non-first players ---
    # We compute it for everyone, then use torch.where at the end.

    # 5. Get trick state
    # Shape: (B, 1)
    leading_trick_suit = (tokens[:, 21] - 10).div(9, rounding_mode='floor').unsqueeze(1)

    # Shape: (B, 2)
    trick_cards = tokens[:, [23, 25]]
    trick_valid = trick_cards != 0
    trick_card_ids = trick_cards.sub(10)
    trick_suits = trick_card_ids.div(9, rounding_mode='floor')
    trick_ranks = trick_card_ids % 9

    # Check which (if any) of the trick cards are valid trump cards
    # Shape: (B, 2)
    is_valid_trump = (trick_suits == trump) & trick_valid

    # Was a cut (trump) played?
    # Shape: (B, 1)
    cut = is_valid_trump.any(dim=1).unsqueeze(1)

    # Get ranks of only the valid trump cards, else -1
    # Shape: (B, 2)
    valid_trump_ranks = trick_ranks.clone()
    valid_trump_ranks[~is_valid_trump] = -1

    # Get the highest trump rank played so far
    # Shape: (B, 1)
    highest_cut_rank = valid_trump_ranks.max(dim=1).values.unsqueeze(1)

    # 6. Get hand properties (relative to trick state)
    # Shape: (B, 9)
    hand_suits = card_ids.div(9, rounding_mode='floor')
    hand_ranks = card_ids % 9

    # Check properties for the *entire hand* (any card)
    # Shape: (B, 1)
    has_suit = ((hand_suits == leading_trick_suit) & valid).any(dim=1).unsqueeze(1)
    has_non_trump_cards = ((hand_suits != trump) & valid).any(dim=1).unsqueeze(1)
    has_higher_trump = ((hand_ranks > highest_cut_rank) & (hand_suits == trump) & valid).any(dim=1).unsqueeze(1)

    # 7. Compute legality for each card in hand
    # This vectorizes the complex 'for card_id in card_ids:' loop
    # Shape: (B, 9)

    is_lead_suit = (hand_suits == leading_trick_suit)
    is_trump_suit = (hand_suits == trump)

    # A card is LEGAL if any of these conditions are met:
    # 1. It matches the lead suit
    legal_cond1 = is_lead_suit

    # 2. It's trump, and no trump has been played yet
    legal_cond2 = is_trump_suit & ~cut

    # 3. It's trump, a trump has been played, and this card is higher
    legal_cond3 = is_trump_suit & cut & (hand_ranks > highest_cut_rank)

    # 4. It's an "under-trump", but it's legal because the hand
    #    contains ONLY trump, and NONE of them are higher.
    legal_cond4 = is_trump_suit & cut & (hand_ranks <= highest_cut_rank) & \
                  ~has_non_trump_cards & ~has_higher_trump

    # 5. It's not lead suit, not trump, and the hand doesn't have the lead suit (discarding)
    legal_cond5 = ~is_lead_suit & ~is_trump_suit & ~has_suit

    # Combine all legal conditions
    is_legal_card = legal_cond1 | legal_cond2 | legal_cond3 | legal_cond4 | legal_cond5

    # 8. Create the complex legal mask (for non-first players)
    # We only want to mark cards as legal if they are *both* valid AND
    # meet the legal conditions.
    # Shape: (B, 9)
    legal_cards_in_hand = valid & is_legal_card

    # Build the final (B, 36) mask using scatter_add_
    complex_mask_float = torch.zeros((B, 36), dtype=torch.float32, device=device)
    # index is the same as from step 3
    src = legal_cards_in_hand.float()
    complex_mask_float.scatter_add_(dim=1, index=index, src=src)
    complex_legal_mask = complex_mask_float > 0

    # 9. Final selection
    # If first_player, use the simple 'base_hand_mask'.
    # Otherwise, use the 'complex_legal_mask' we just built.
    final_mask = torch.where(is_first_player, base_hand_mask, complex_legal_mask)

    return final_mask