# dataset.py
from glob import glob

import numpy as np
import torch
from torch.utils.data import Dataset, Subset

from rl.tokens import Tokens

SAMPLE_LENGTH = 97
TOKEN_LENGTH = 96
VOCABULARY_SIZE = 128


class JassBinaryDataset(Dataset):
    def __init__(self, data_dir: str):
        self.files = sorted(glob(f"{data_dir}/*.dat"))
        print(f"Found {len(self.files)} binary files")

        # Memory-map all files
        self.mmaps = []
        self.offsets = [0]
        self.total_samples = 0
        for f in self.files:
            arr = np.memmap(f, dtype=np.uint8, mode="r")
            samples = len(arr) // SAMPLE_LENGTH
            self.total_samples += samples
            self.mmaps.append(arr)
            self.offsets.append(self.offsets[-1] + samples)
        print(f"Total samples: {self.total_samples:,}")

    def __len__(self):
        return self.offsets[-1]

    def __getitem__(self, idx):
        # Binary search file
        file_idx = np.searchsorted(self.offsets, idx, side="right") - 1
        local_idx = idx - self.offsets[file_idx]
        offset = local_idx * SAMPLE_LENGTH

        data = self.mmaps[file_idx]
        tokens = torch.from_numpy(data[offset : offset + TOKEN_LENGTH].astype(np.int64))
        action = int(data[offset + TOKEN_LENGTH])

        return tokens, torch.tensor(action, dtype=torch.long)


def _is_round_start(data, sample_idx: int) -> bool:
    """A round starts with the trump choice, or with the first card decision (full hand) if we did not choose trump."""
    offset = sample_idx * SAMPLE_LENGTH
    phase = data[offset + 2]
    if phase == Tokens.CHOOSE_TRUMP_SUIT:
        return True
    has_full_hand = data[offset + 19] != Tokens.PAD
    previous_is_trump_choice = sample_idx > 0 and data[offset - SAMPLE_LENGTH + 2] == Tokens.CHOOSE_TRUMP_SUIT
    return has_full_hand and not previous_is_trump_choice


def split_train_val(dataset: JassBinaryDataset, val_fraction: float) -> tuple[Subset, Subset]:
    """
    Splits the dataset in time rather than at random: the last `val_fraction` of each file goes to validation,
    cut at a round start. Consecutive decisions of a game are near duplicates, and the files of the 4 seats go
    through the same games, so a random split would put near copies of the validation samples in the training set.
    """
    train_indices, val_indices = [], []
    for file_idx, data in enumerate(dataset.mmaps):
        first, end = dataset.offsets[file_idx], dataset.offsets[file_idx + 1]
        num_samples = end - first
        cut = int(num_samples * (1 - val_fraction))
        while cut < num_samples and not _is_round_start(data, cut):
            cut += 1
        train_indices.extend(range(first, first + cut))
        val_indices.extend(range(first + cut, end))
    return Subset(dataset, train_indices), Subset(dataset, val_indices)
