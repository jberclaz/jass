# train.py
import argparse
import subprocess
from collections import defaultdict

import mlflow
import mlflow.pytorch
import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader
from tqdm import tqdm

from dataset import JassBinaryDataset, split_train_val
from legal_mask import get_legal_mask_with_rules_batch, get_trump_mask_batch
from model import JassFormer
from rl.export_checkpoint_to_onnx import export_supervised_model_to_onnx
from rl.tokens import Tokens


class Config:
    data_path = "training_data/"
    batch_size = 512
    epochs = 10
    lr = 3e-4
    weight_decay = 1e-5
    grad_clip = 1.0
    d_model = 256
    val_fraction = 0.05
    device = "cuda" if torch.cuda.is_available() else "cpu"
    log_every = 100
    save_every = 5000
    best_model_path = "jassformer_best.pt"
    onnx_model_path = "jassformer.onnx"
    mlflow_run_name = "jassformer_v2"
    mlflow_experiment = "JassFormer"
    mlflow_tracking_uri = "sqlite:///mlflow.db"


class Metrics:
    """
    Accumulates loss and accuracies. Forced moves (a single legal card) are always "correct" and teach nothing,
    so card accuracy is also reported on the other moves, next to the accuracy of a uniformly random legal move.
    """

    def __init__(self):
        self._sums = defaultdict(float)

    def update(self, tokens, action, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask, loss_sum):
        is_card_play = tokens[:, 2] == Tokens.CHOOSE_NEXT_CARD
        is_trump_choice = tokens[:, 2] == Tokens.CHOOSE_TRUMP_SUIT
        num_legal_cards = legal_mask.sum(dim=-1)
        is_unforced = is_card_play & (num_legal_cards > 1)
        card_correct = card_log_probs.argmax(dim=-1) == action
        trump_correct = trump_log_probs.argmax(dim=-1) == action

        s = self._sums
        s["samples"] += action.size(0)
        s["loss"] += loss_sum
        s["card"] += is_card_play.sum().item()
        s["card_correct"] += (card_correct & is_card_play).sum().item()
        s["unforced"] += is_unforced.sum().item()
        s["unforced_correct"] += (card_correct & is_unforced).sum().item()
        s["unforced_random"] += (1.0 / num_legal_cards[is_unforced].float()).sum().item()
        s["trump"] += is_trump_choice.sum().item()
        s["trump_correct"] += (trump_correct & is_trump_choice).sum().item()
        s["trump_random"] += (1.0 / trump_legal_mask.sum(dim=-1)[is_trump_choice].float()).sum().item()

    def summary(self, prefix: str) -> dict[str, float]:
        s = self._sums
        ratio = lambda num, den: s[num] / s[den] if s[den] > 0 else float("nan")
        return {
            f"{prefix}/loss": ratio("loss", "samples"),
            f"{prefix}/card_acc": ratio("card_correct", "card"),
            f"{prefix}/card_acc_unforced": ratio("unforced_correct", "unforced"),
            f"{prefix}/card_acc_unforced_random": ratio("unforced_random", "unforced"),
            f"{prefix}/trump_acc": ratio("trump_correct", "trump"),
            f"{prefix}/trump_acc_random": ratio("trump_random", "trump"),
        }


def format_summary(summary: dict[str, float]) -> str:
    values = {k.split("/", 1)[1]: v for k, v in summary.items()}
    return (f"loss {values['loss']:.4f} | card {values['card_acc'] * 100:.2f}% | "
            f"unforced card {values['card_acc_unforced'] * 100:.2f}% (random {values['card_acc_unforced_random'] * 100:.2f}%) | "
            f"trump {values['trump_acc'] * 100:.2f}% (random {values['trump_acc_random'] * 100:.2f}%)")


def log_model(model, name: str):
    # pickle: MLflow's default format (pt2) traces the model and requires an input example
    mlflow.pytorch.log_model(model, name=name, serialization_format="pickle")


def forward_and_loss(model, tokens, action):
    legal_mask = get_legal_mask_with_rules_batch(tokens)
    trump_legal_mask = get_trump_mask_batch(tokens)
    card_log_probs, trump_log_probs = model(tokens, legal_mask, trump_legal_mask)

    # each sample uses the head of its phase
    is_trump_choice = tokens[:, 2] == Tokens.CHOOSE_TRUMP_SUIT
    card_nll = F.nll_loss(card_log_probs, action.clamp(max=35), reduction="none")
    trump_nll = F.nll_loss(trump_log_probs, action.clamp(max=4), reduction="none")
    loss_sum = torch.where(is_trump_choice, trump_nll, card_nll).sum()
    return loss_sum, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask


def evaluate(val_loader, model, device) -> dict[str, float]:
    model.eval()
    metrics = Metrics()
    with torch.no_grad():
        for tokens, action in tqdm(val_loader, desc="Validating"):
            tokens = tokens.to(device)
            action = action.to(device)
            loss_sum, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask = forward_and_loss(model, tokens, action)
            metrics.update(tokens, action, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask, loss_sum.item())
    model.train()
    return metrics.summary("val")


def train():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, default="data/")
    parser.add_argument("--batch", type=int, default=Config.batch_size)
    parser.add_argument("--epochs", type=int, default=Config.epochs)
    parser.add_argument("--lr", type=float, default=Config.lr)
    parser.add_argument("--d_model", type=int, default=Config.d_model)
    parser.add_argument("--val_fraction", type=float, default=Config.val_fraction,
                        help="fraction of each data file (its last games) used for validation")
    parser.add_argument("--description", "-d", type=str, default="")
    args = parser.parse_args()

    cfg = Config()
    cfg.data_path = args.data
    cfg.batch_size = args.batch
    cfg.epochs = args.epochs
    cfg.lr = args.lr
    cfg.d_model = args.d_model
    cfg.val_fraction = args.val_fraction
    # MLflow keeps defaulting to the deprecated ./mlruns file store whenever that directory still holds runs
    # (e.g. after `mlflow migrate-filestore`), so pick the database explicitly unless MLFLOW_TRACKING_URI is set
    if not mlflow.is_tracking_uri_set():
        mlflow.set_tracking_uri(cfg.mlflow_tracking_uri)
    mlflow.set_experiment(cfg.mlflow_experiment)

    with mlflow.start_run(run_name=cfg.mlflow_run_name) as run:
        dataset = JassBinaryDataset(cfg.data_path)
        train_set, val_set = split_train_val(dataset, cfg.val_fraction)
        print(f"Training samples: {len(train_set):,} | validation samples: {len(val_set):,}")

        train_loader = DataLoader(train_set, batch_size=cfg.batch_size, shuffle=True, num_workers=8, pin_memory=True)
        val_loader = DataLoader(val_set, batch_size=cfg.batch_size, shuffle=False, num_workers=8)

        device = torch.device(cfg.device)
        model = JassFormer(d_model=cfg.d_model).to(device)
        optimizer = torch.optim.AdamW(model.parameters(), lr=cfg.lr, weight_decay=cfg.weight_decay)
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=cfg.epochs)

        mlflow.log_params(
            {
                "batch_size": cfg.batch_size,
                "epochs": cfg.epochs,
                "lr": cfg.lr,
                "weight_decay": cfg.weight_decay,
                "d_model": cfg.d_model,
                "val_fraction": cfg.val_fraction,
                "device": cfg.device,
                "data_samples": dataset.total_samples,
                "train_samples": len(train_set),
                "val_samples": len(val_set),
                "git_commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(),
            }
        )
        run_description = f"""
            ## Run: `{run.info.run_id}`
            - **Data**: `{cfg.data_path}` ({len(dataset)} samples, last {cfg.val_fraction:.0%} of each file for validation)
            - **Legal Mask**: `get_legal_mask_with_rules_batch` (vectorized)
            - **Description**: `{args.description}`
        """
        mlflow.set_tag("mlflow.note.content", run_description)

        step = 0
        best_val_loss = float("inf")
        best_summary = None
        for epoch in range(cfg.epochs):
            model.train()
            epoch_metrics = Metrics()
            log_metrics = Metrics()

            for tokens, action in tqdm(train_loader, desc=f"Epoch {epoch + 1}/{cfg.epochs}"):
                step += 1
                tokens = tokens.to(device)
                action = action.to(device)

                loss_sum, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask = forward_and_loss(model, tokens, action)
                loss = loss_sum / action.size(0)

                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), cfg.grad_clip)
                optimizer.step()

                with torch.no_grad():
                    for metrics in (epoch_metrics, log_metrics):
                        metrics.update(tokens, action, card_log_probs, trump_log_probs, legal_mask, trump_legal_mask,
                                       loss_sum.item())

                # === LOGGING ===
                if step % cfg.log_every == 0:
                    mlflow.log_metrics(log_metrics.summary("train") | {"train/lr": optimizer.param_groups[0]["lr"]},
                                       step=step)
                    log_metrics = Metrics()

                if step % cfg.save_every == 0:
                    log_model(model, f"model_step{step}")

            train_summary = epoch_metrics.summary("epoch")
            print(f"Train: {format_summary(train_summary)}")
            mlflow.log_metrics(train_summary, step=step)

            # Validation
            val_summary = evaluate(val_loader, model, device)
            mlflow.log_metrics(val_summary, step=step)
            print(f"Val:   {format_summary(val_summary)}")
            if val_summary["val/loss"] < best_val_loss:
                best_val_loss = val_summary["val/loss"]
                best_summary = val_summary
                torch.save(model.state_dict(), cfg.best_model_path)
                mlflow.pytorch.log_state_dict(model.state_dict(), f"best_model_step{step}")
                mlflow.log_artifact(cfg.best_model_path)
                print("SAVED BEST MODEL")

            scheduler.step()

        print(f"Training complete! Best val: {format_summary(best_summary)}")
        mlflow.log_metrics({k.replace("val/", "final/best_"): v for k, v in best_summary.items()})

        # export the best model (in eval mode, on CPU) for the Java client
        export_supervised_model_to_onnx(cfg.best_model_path, cfg.onnx_model_path, cfg.d_model)
        print(f"Exported to {cfg.onnx_model_path}")
        log_model(model, "final_model")
        mlflow.log_artifact(cfg.onnx_model_path)


if __name__ == "__main__":
    train()
