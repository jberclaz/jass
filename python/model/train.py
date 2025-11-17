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

from dataset import JassBinaryDataset, TOKEN_LENGTH, VOCABULARY_SIZE
from model import JassFormer
from legal_mask import get_legal_mask_with_rules, get_legal_mask_with_rules_batch, get_trump_mask_batch


class Config:
    data_path = "training_data/"
    batch_size = 512
    epochs = 10
    lr = 3e-4
    weight_decay = 1e-5
    grad_clip = 1.0
    device = "cuda" if torch.cuda.is_available() else "cpu"
    log_every = 100
    eval_every = 1000  # currently unused
    save_every = 5000
    mlflow_run_name = "jassformer_v2"
    mlflow_experiment = "JassFormer"


def evaluate(val_loader, model, device) -> float:
    model.eval()
    val_correct = 0
    val_total = 0
    with torch.no_grad():
        for tokens, action in tqdm(val_loader, desc="Validating"):
            tokens = tokens.to(device)
            action = action.to(device)

            legal_mask = get_legal_mask_with_rules_batch(tokens).to(device)
            trump_legal_mask = get_trump_mask_batch(tokens).to(device)

            card_log_probs, trump_log_probs = model(tokens, legal_mask, trump_legal_mask                                                    )

            is_card_play = (tokens[:, 2] == 125)
            is_trump_choice = (tokens[:, 2] == 126)
            card_play_indices = is_card_play.nonzero(as_tuple=True)[0]
            trump_choice_indices = is_trump_choice.nonzero(as_tuple=True)[0]

            correct_card = 0
            correct_trump = 0

            # 1. Calculate accuracy for the card-play samples (if any exist)
            if card_play_indices.numel() > 0:
                # Get predictions for card-play phase
                pred_card = card_log_probs[card_play_indices].argmax(dim=-1)

                # Compare with correct actions for this phase
                correct_card = (pred_card == action[card_play_indices]).sum().item()

            # 2. Calculate accuracy for the trump-choice samples (if any exist)
            if trump_choice_indices.numel() > 0:
                # Get predictions for trump-choice phase
                pred_trump = trump_log_probs[trump_choice_indices].argmax(dim=-1)

                # Compare with correct actions for this phase
                correct_trump = (pred_trump == action[trump_choice_indices]).sum().item()

            # 3. Add correct predictions from *both* phases
            val_correct += (correct_card + correct_trump)
            val_total += action.size(0)

    model.train()
    return val_correct / val_total


def train():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, default="data/")
    parser.add_argument("--batch", type=int, default=512)
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--description", "-d", type=str, default="")
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

        mlflow.log_params(
            {
                "batch_size": cfg.batch_size,
                "epochs": cfg.epochs,
                "lr": cfg.lr,
                "weight_decay": cfg.weight_decay,
                "device": cfg.device,
                "data_samples": dataset.total_samples,
                "git_commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(),
            }
        )
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

                legal_mask = get_legal_mask_with_rules_batch(tokens).to(device)
                trump_legal_mask = get_trump_mask_batch(tokens).to(device)

                card_log_probs, trump_log_probs = model(tokens, legal_mask, trump_legal_mask)

                is_card_play = (tokens[:, 2] == 125)
                is_trump_choice = (tokens[:, 2] == 126)
                card_play_indices = is_card_play.nonzero(as_tuple=True)[0]
                trump_choice_indices = is_trump_choice.nonzero(as_tuple=True)[0]

                # 3. Initialize loss components
                loss_card = torch.tensor(0.0, device=device)
                loss_trump = torch.tensor(0.0, device=device)

                # 4. Calculate the total loss for the card-play samples (if any exist)
                if card_play_indices.numel() > 0:
                    loss_card = F.nll_loss(
                        card_log_probs[card_play_indices],  # Select "card play" rows
                        action[card_play_indices],  # Select corresponding actions
                        reduction='sum'  # We get the sum of losses for this group
                    )

                # 5. Calculate the total loss for the trump-choice samples (if any exist)
                if trump_choice_indices.numel() > 0:
                    loss_trump = F.nll_loss(
                        trump_log_probs[trump_choice_indices],  # Select "trump choice" rows
                        action[trump_choice_indices],  # Select corresponding actions
                        reduction='sum'  # We get the sum of losses for this group
                    )

                # 6. Combine the sums and average over the *total* batch size
                loss = (loss_card + loss_trump) / cfg.batch_size

                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()

                total_loss += loss.item()

                correct_card = 0
                correct_trump = 0

                # 1. Calculate accuracy for the card-play samples (if any exist)
                if card_play_indices.numel() > 0:
                    # Get predictions for card-play phase
                    pred_card = card_log_probs[card_play_indices].argmax(dim=-1)

                    # Compare with correct actions for this phase
                    correct_card = (pred_card == action[card_play_indices]).sum().item()

                # 2. Calculate accuracy for the trump-choice samples (if any exist)
                if trump_choice_indices.numel() > 0:
                    # Get predictions for trump-choice phase
                    pred_trump = trump_log_probs[trump_choice_indices].argmax(dim=-1)

                    # Compare with correct actions for this phase
                    correct_trump = (pred_trump == action[trump_choice_indices]).sum().item()

                # 3. Add correct predictions from *both* phases
                correct += (correct_card + correct_trump)
                total += action.size(0)

                # === LOGGING ===
                if step % cfg.log_every == 0:
                    acc = correct / total
                    mlflow.log_metrics(
                        {
                            "train/loss": loss.item(),
                            "train/acc": acc,
                            "train/lr": optimizer.param_groups[0]["lr"],
                        },
                        step=step,
                    )

                if step % cfg.save_every == 0:
                    mlflow.pytorch.log_model(model, f"model_step{step}")

            acc = correct / total
            print(f"Train Loss: {total_loss / len(train_loader):.4f} | Acc: {acc * 100:.2f}%")
            mlflow.log_metric("epoch/acc", acc, step=step)

            # Validation
            val_acc = evaluate(val_loader, model, device)
            mlflow.log_metric("val/acc", val_acc, step=step)
            print(f"Val Acc: {val_acc * 100:.2f}%")
            if val_acc > best_acc:
                best_acc = val_acc
                torch.save(model.state_dict(), "jassformer_best.pt")
                mlflow.pytorch.log_state_dict(model.state_dict(), f"best_model_step{step}")
                mlflow.log_artifact("jassformer_best.pt")
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
            dynamic_shapes=({0: Dim("batch", min=1)},),
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
