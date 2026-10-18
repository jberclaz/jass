# dataset.py
from glob import glob

import numpy as np
import torch
from torch.utils.data import Dataset, Subset

from rl.tokens import Tokens

TOKEN_LENGTH = 96
VOCABULARY_SIZE = 128
NUM_SCORES = 36
# .dat: the tokens and the action played
SAMPLE_LENGTH = TOKEN_LENGTH + 1
# .sdat: also Monte Carlo's score of each action, big-endian int16 in tenths of a point (see ArtificialPlayer)
SCORED_SAMPLE_LENGTH = SAMPLE_LENGTH + 2 * NUM_SCORES
NO_SCORE = -32768


class JassBinaryDataset(Dataset):
    """
    Reads the .dat and .sdat files of a directory. Each sample is (tokens, action, scores): scores holds Monte Carlo's
    expected point difference until the end of the hand for each card (by card number), or for each trump suit (the
    first 4) for a trump choice, NaN where there is none (illegal cards, forced moves, and all the .dat samples).
    """

    def __init__(self, data_dir: str):
        self.files = sorted(glob(f"{data_dir}/*.dat") + glob(f"{data_dir}/*.sdat"))
        print(f"Found {len(self.files)} binary files")

        # Memory-map all files
        self.mmaps = []
        self.sample_lengths = []
        self.offsets = [0]
        self.total_samples = 0
        self.scored_samples = 0
        for f in self.files:
            arr = np.memmap(f, dtype=np.uint8, mode="r")
            sample_length = SCORED_SAMPLE_LENGTH if f.endswith(".sdat") else SAMPLE_LENGTH
            samples = len(arr) // sample_length
            self.total_samples += samples
            if sample_length == SCORED_SAMPLE_LENGTH:
                self.scored_samples += samples
            self.mmaps.append(arr)
            self.sample_lengths.append(sample_length)
            self.offsets.append(self.offsets[-1] + samples)
        print(f"Total samples: {self.total_samples:,} ({self.scored_samples:,} with scores)")

    def __len__(self):
        return self.offsets[-1]

    def __getitem__(self, idx):
        # Binary search file
        file_idx = np.searchsorted(self.offsets, idx, side="right") - 1
        local_idx = idx - self.offsets[file_idx]
        sample_length = self.sample_lengths[file_idx]
        offset = local_idx * sample_length

        data = self.mmaps[file_idx]
        tokens = torch.from_numpy(data[offset : offset + TOKEN_LENGTH].astype(np.int64))
        action = int(data[offset + TOKEN_LENGTH])
        scores = np.full(NUM_SCORES, np.nan, dtype=np.float32)
        if sample_length == SCORED_SAMPLE_LENGTH:
            raw = np.frombuffer(data[offset + SAMPLE_LENGTH : offset + sample_length].tobytes(), dtype=">i2")
            known = raw != NO_SCORE
            scores[known] = raw[known] / 10

        return tokens, torch.tensor(action, dtype=torch.long), torch.from_numpy(scores)


def _is_round_start(data, sample_idx: int, sample_length: int = SAMPLE_LENGTH) -> bool:
    """A round starts with the trump choice, or with the first card decision (full hand) if we did not choose trump."""
    offset = sample_idx * sample_length
    phase = data[offset + 2]
    if phase == Tokens.CHOOSE_TRUMP_SUIT:
        return True
    has_full_hand = data[offset + 19] != Tokens.PAD
    previous_is_trump_choice = sample_idx > 0 and data[offset - sample_length + 2] == Tokens.CHOOSE_TRUMP_SUIT
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
        while cut < num_samples and not _is_round_start(data, cut, dataset.sample_lengths[file_idx]):
            cut += 1
        train_indices.extend(range(first, first + cut))
        val_indices.extend(range(first + cut, end))
    return Subset(dataset, train_indices), Subset(dataset, val_indices)
