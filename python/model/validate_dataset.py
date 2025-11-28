import argparse

from torch.utils.data import DataLoader
from tqdm import tqdm

from dataset import JassBinaryDataset
import legal_mask as lm

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, default="data/")
    parser.add_argument("--batch", type=int, default=512)
    args = parser.parse_args()

    dataset = JassBinaryDataset(args.data)

    train_loader = DataLoader(dataset, batch_size=args.batch, shuffle=False, num_workers=8, pin_memory=True)

    for tokens, action in tqdm(train_loader):
        legal_mask = lm.get_legal_mask_with_rules_batch(tokens)
        trump_legal_mask = lm.get_trump_mask_batch(tokens)

        for b in range(args.batch):
            a = action[b]
            if tokens[b][2] == 125:
                if not legal_mask[b, a]:
                    rule_legal_mask = lm.get_legal_mask_with_rules(tokens[b])
                    print(tokens[b])
                    raise RuntimeError(f"Bad mask at {b}")
            else:
                if not trump_legal_mask[b, a]:
                    raise RuntimeError(f"Bad trump mask at {b}")



if __name__ == "__main__":
    main()
