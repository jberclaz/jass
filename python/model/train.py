# train.py
import argparse
from pathlib import Path
import subprocess

import torch
import torch.nn.functional as F
from torch.export import Dim  # <-- 1. Import Dim
from torch.utils.data import DataLoader, random_split
from tqdm import tqdm
import mlflow
import mlflow.pytorch

from dataset import JassBinaryDataset, TOKEN_LENGTH, VOCABULARY_SIZE, SAMPLE_LENGTH
from model import JassFormer

class Config:
    data_path = "training_data/"
    batch_size = 512
    epochs = 10
    lr = 3e-4
    weight_decay = 1e-5
    grad_clip = 1.0
    device = "cuda" if torch.cuda.is_available() else "cpu"
    log_every = 100
    eval_every = 1000
    save_every = 5000
    mlflow_run_name = "jassformer_v2"
    mlflow_experiment = "JassFormer"

def get_legal_mask(hand_tokens):
    # Extract hand cards (positions 10-18)
    hand = hand_tokens[10:19]
    valid = (hand != 0)
    card_ids = (hand[valid] - 10).clamp(0, 35)
    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    return mask

def get_legal_mask_with_rules(tokens: torch.Tensor) -> torch.Tensor:
    trump = tokens[4] - 56
    hand_tokens = tokens[10:19]
    valid = (hand_tokens != 0)
    card_ids = (hand_tokens[valid] - 10)

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

@torch.no_grad()
def get_legal_mask_fast(tokens: torch.Tensor) -> torch.Tensor:
    """
    Ultra-fast Jass legal mask.
    Input: tokens [B, 95], int64
    Output: legal_mask [B, 36], bool
    """
    B = tokens.shape[0]
    device = tokens.device

    # === 1. Constants (match your encoding) ===
    TRUMP_POS = 4
    TRUMP_OFFSET = 56
    HAND_START, HAND_END = 10, 19
    LEAD_POS = 21              # leading card token
    PLIE_POS = [21, 23, 25] # up to 4 cards in plie (adjust if needed)

    # === 2. Extract hand ===
    hand_tokens = tokens[:, HAND_START:HAND_END]           # [B,9]
    hand_valid = hand_tokens != 0                          # [B,9]
    hand_cards = hand_tokens - 10                          # [B,9], -10..26 or 0
    hand_cards = hand_cards.masked_fill(~hand_valid, -1)   # invalid = -1

    # Flatten hand cards: [B*9]
    flat_hand = hand_cards.view(B, -1)                     # [B,9]
    valid_mask = hand_valid.view(B, -1)                    # [B,9]
    flat_cards = flat_hand[valid_mask]                     # [N_valid]

    # Base mask: only cards in hand
    mask = torch.zeros(B, 36, dtype=torch.bool, device=device)
    if flat_cards.numel() > 0:
        mask.view(-1).scatter_(1, flat_cards.unsqueeze(0), True)

    # === 3. First to play? ===
    lead_token = tokens[:, LEAD_POS]
    is_first = lead_token == 0
    if is_first.any():
        mask[is_first] = hand_valid[is_first]

    # === 4. Trump & leading suit ===
    trump = tokens[:, TRUMP_POS] - TRUMP_OFFSET            # [B], 0..3
    lead_suit = ((lead_token - 10) // 9).clamp(0, 3)       # [B]

    # === 5. Plie trumps (for undercut) ===
    plie_tokens = tokens[:, PLIE_POS]                      # [B,4]
    plie_cards = plie_tokens - 10
    plie_valid = plie_tokens != 0
    plie_suits = (plie_cards // 9).clamp(0, 3)
    plie_ranks = plie_cards % 9

    is_trump_played = (plie_suits == trump.unsqueeze(1)) & plie_valid
    has_trump_in_plie = is_trump_played.any(dim=1)         # [B]
    highest_trump_rank = torch.where(
        is_trump_played,
        plie_ranks,
        torch.full_like(plie_ranks, -1)
    ).max(dim=1).values                                    # [B]

    # === 6. Player has leading suit? ===
    hand_suits = (hand_cards // 9).clamp(0, 3)             # [B,9]
    has_leading_suit = ((hand_suits == lead_suit.unsqueeze(1)) & hand_valid).any(dim=1)

    # === 7. Build final mask (vectorized!) ===
    card_ids = torch.arange(36, device=device)
    card_suit = card_ids // 9
    card_rank = card_ids % 9

    # Expand to [B,36]
    suit_b = card_suit.unsqueeze(0).expand(B, -1)
    rank_b = card_rank.unsqueeze(0).expand(B, -1)
    in_hand = mask.clone()

    # Rule 1: Must follow suit
    must_follow = ~is_first.unsqueeze(1) & has_leading_suit.unsqueeze(1)
    follow_ok = suit_b == lead_suit.unsqueeze(1)
    mask = mask & (~must_follow | follow_ok)

    # Rule 2: Trump undercut
    can_trump = ~is_first.unsqueeze(1) & ~has_leading_suit.unsqueeze(1)
    trump_card = suit_b == trump.unsqueeze(1)
    higher_trump = rank_b > highest_trump_rank.unsqueeze(1)

    # Can undercut OR no trump in plie OR all trumps case
    undercut_ok = has_trump_in_plie.unsqueeze(1) & higher_trump
    no_trump_in_plie = ~has_trump_in_plie.unsqueeze(1)
    all_trumps_case = can_trump & ~has_leading_suit.unsqueeze(1)

    mask = mask | (can_trump & trump_card & (undercut_ok | no_trump_in_plie | all_trumps_case))

    # Rule 3: Off-suit only if no leading suit
    off_suit_ok = ~has_leading_suit.unsqueeze(1) & ~is_first.unsqueeze(1)
    off_suit = (suit_b != lead_suit.unsqueeze(1)) & (suit_b != trump.unsqueeze(1))
    mask = mask | (off_suit_ok & off_suit & in_hand)

    return mask & in_hand  # final: only cards in hand

def evaluate(val_loader, model, device) -> float:
    model.eval()
    val_correct = 0
    val_total = 0
    with torch.no_grad():
        for tokens, action in tqdm(val_loader, desc="Validating"):
            tokens = tokens.to(device)
            action = action.to(device)
            legal_mask = torch.stack([get_legal_mask_with_rules(t) for t in tokens]).to(device)
            log_probs = model(tokens, legal_mask)
            pred = log_probs.argmax(dim=-1)
            val_correct += (pred == action).sum().item()
            val_total += action.size(0)
    model.train()
    return val_correct / val_total

def train():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data', type=str, default='data/')
    parser.add_argument('--batch', type=int, default=512)
    parser.add_argument('--epochs', type=int, default=10)
    parser.add_argument('--lr', type=float, default=3e-4)
    parser.add_argument('--description', '-d', type=str, default='')
    args = parser.parse_args()

    cfg = Config()
    if args.data:
        cfg.data_path = args.data
    if args.batch:
        cfg.batch_size = args.batch
    if args.epochs:
        cfg.epochs = args.epochs
    if args.lr:
        cfg.lr = args.lr
    mlflow.set_experiment(cfg.mlflow_experiment)

    with mlflow.start_run(run_name=cfg.mlflow_run_name) as run:
        dataset = JassBinaryDataset(cfg.data_path)
        train_size = int(0.95 * len(dataset))
        val_size = len(dataset) - train_size
        train_set, val_set = random_split(dataset, [train_size, val_size])

        train_loader = DataLoader(train_set, batch_size=cfg.batch_size, shuffle=True, num_workers=8, pin_memory=True)
        val_loader = DataLoader(val_set, batch_size=cfg.batch_size, shuffle=False, num_workers=8)

        device = torch.device(cfg.device)
        model = JassFormer().to(device)
        optimizer = torch.optim.AdamW(model.parameters(), lr=cfg.lr, weight_decay=cfg.weight_decay)
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=cfg.epochs)

        mlflow.log_params({
            "batch_size": cfg.batch_size,
            "epochs": cfg.epochs,
            "lr": cfg.lr,
            "weight_decay": cfg.weight_decay,
            "device": cfg.device,
            "data_samples": dataset.total_samples,
            "git_commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(),
        })
        run_description = f"""
            ## Run: `{run.info.run_id}`
            - **Data**: `{cfg.data_path}` ({len(dataset)} samples)
            - **Legal Mask**: `get_legal_mask_fast` (vectorized)
            - **Description**: `{args.description}`
        """
        mlflow.set_tag("mlflow.note.content", run_description)

        step = 0
        best_acc = 0
        for epoch in range(cfg.epochs):
            model.train()
            total_loss = 0
            correct = 0
            total = 0

            for tokens, action in tqdm(train_loader, desc=f"Epoch {epoch + 1}/{cfg.epochs}"):
                step += 1
                tokens = tokens.to(device)
                action = action.to(device)

                legal_mask = torch.stack([get_legal_mask_with_rules(t) for t in tokens]).to(device)

                log_probs = model(tokens, legal_mask)
                loss = F.nll_loss(log_probs, action)

                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()

                total_loss += loss.item()
                pred = log_probs.argmax(dim=-1)
                correct += (pred == action).sum().item()
                total += action.size(0)

                # === LOGGING ===
                if step % cfg.log_every == 0:
                    acc = correct / total
                    mlflow.log_metrics({
                        "train/loss": loss.item(),
                        "train/acc": acc,
                        "train/lr": optimizer.param_groups[0]['lr'],
                    }, step=step)

                # === EVAL & SAVE ===
                if step % cfg.eval_every == 0:
                    val_acc = evaluate(val_loader, model, device)
                    mlflow.log_metric("val/acc", val_acc, step=step)
                    print(f"Step {step} | Val Acc: {val_acc:.4f}")

                    if val_acc > best_acc:
                        best_acc = val_acc
                        checkpoint = {
                            "model": model.state_dict(),
                            "opt": optimizer.state_dict(),
                            "step": step,
                            "acc": val_acc,
                        }
                        mlflow.pytorch.log_state_dict(model.state_dict(), f"best_model_step{step}")
                        torch.save(checkpoint, "best.pt")
                        mlflow.log_artifact("best.pt")

                if step % cfg.save_every == 0:
                    mlflow.pytorch.log_model(model, f"model_step{step}")

            acc = correct / total
            print(f"Train Loss: {total_loss / len(train_loader):.4f} | Acc: {acc * 100:.2f}%")
            mlflow.log_metric("epoch/acc", acc, step=step)

            # Validation
            val_acc = evaluate(val_loader, model, device)
            print(f"Val Acc: {val_acc * 100:.2f}%")

            if val_acc > best_acc:
                best_acc = val_acc
                torch.save(model.state_dict(), "jassformer_best.pt")
                print("SAVED BEST MODEL")

            scheduler.step()

        onnx_model = "jassformer.onnx"
        onnx_model_data = onnx_model + ".data"
        print(f"Training complete! Best val acc: {best_acc * 100:.2f}%")
        torch.onnx.export(
            model,
    (torch.randint(0, VOCABULARY_SIZE, (1, TOKEN_LENGTH)).cuda(),),
            onnx_model,
            opset_version=18,
            input_names=["tokens"],
            output_names=["logits"],
            dynamic_shapes=({0: Dim("batch", min=1)},)
        )
        print(f"Exported to {onnx_model}")
        mlflow.log_metric("final/best_acc", best_acc)
        mlflow.pytorch.log_model(model, "final_model")
        mlflow.log_artifact(onnx_model)
        if Path(onnx_model_data).exists():
            data_size = Path(onnx_model_data).stat().st_size / (1024 * 1024)  # MB
            if data_size > 1.0:  # only if external
                mlflow.log_artifact(onnx_model_data)

if __name__ == "__main__":
    train()
