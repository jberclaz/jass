# train.py
import argparse

import torch
import torch.nn.functional as F
from torch.export import Dim  # <-- 1. Import Dim
from torch.utils.data import DataLoader, random_split
from tqdm import tqdm

from dataset import JassBinaryDataset, TOKEN_LENGTH, VOCABULARY_SIZE
from model import JassFormer


def get_legal_mask(hand_tokens):
    # Extract hand cards (positions 10-18)
    hand = hand_tokens[10:19]
    valid = (hand != 0)
    card_ids = (hand[valid] - 10).clamp(0, 35)
    mask = torch.zeros(36, dtype=torch.bool)
    mask[card_ids] = True
    return mask


def train():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data', type=str, default='data/')
    parser.add_argument('--batch', type=int, default=512)
    parser.add_argument('--epochs', type=int, default=10)
    parser.add_argument('--lr', type=float, default=3e-4)
    args = parser.parse_args()

    dataset = JassBinaryDataset(args.data)
    train_size = int(0.95 * len(dataset))
    val_size = len(dataset) - train_size
    train_set, val_set = random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_set, batch_size=args.batch, shuffle=True, num_workers=8, pin_memory=True)
    val_loader = DataLoader(val_set, batch_size=args.batch, shuffle=False, num_workers=8)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model = JassFormer().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-5)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_acc = 0
    for epoch in range(args.epochs):
        model.train()
        total_loss = 0
        correct = 0
        total = 0

        for tokens, action in tqdm(train_loader, desc=f"Epoch {epoch + 1}/{args.epochs}"):
            tokens = tokens.to(device)
            action = action.to(device)

            legal_mask = torch.stack([get_legal_mask(t) for t in tokens]).to(device)

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

        acc = correct / total
        print(f"Train Loss: {total_loss / len(train_loader):.4f} | Acc: {acc * 100:.2f}%")

        # Validation
        model.eval()
        val_correct = 0
        val_total = 0
        with torch.no_grad():
            for tokens, action in tqdm(val_loader, desc="Validating"):
                tokens = tokens.to(device)
                action = action.to(device)
                legal_mask = torch.stack([get_legal_mask(t) for t in tokens]).to(device)
                log_probs = model(tokens, legal_mask)
                pred = log_probs.argmax(dim=-1)
                val_correct += (pred == action).sum().item()
                val_total += action.size(0)

        val_acc = val_correct / val_total
        print(f"Val Acc: {val_acc * 100:.2f}%")

        if val_acc > best_acc:
            best_acc = val_acc
            torch.save(model.state_dict(), "jassformer_best.pt")
            print("SAVED BEST MODEL")

        scheduler.step()

    print(f"Training complete! Best val acc: {best_acc * 100:.2f}%")
    torch.onnx.export(
        model,
(torch.randint(0, VOCABULARY_SIZE, (1, TOKEN_LENGTH)).cuda(),),
        "jassformer.onnx",
        opset_version=18,
        input_names=["tokens"],
        output_names=["logits"],
        dynamic_shapes=({0: Dim("batch", min=1)},)
    )
    print("Exported to jassformer.onnx")


if __name__ == "__main__":
    train()