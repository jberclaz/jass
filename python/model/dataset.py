# dataset.py
from glob import glob

import numpy as np
import torch
from torch.utils.data import Dataset

SAMPLE_LENGTH = 96
TOKEN_LENGTH = 95
VOCABULARY_SIZE =128

class JassBinaryDataset(Dataset):
    def __init__(self, data_dir: str):
        self.files = sorted(glob(f"{data_dir}/*.dat"))
        print(f"Found {len(self.files)} binary files")

        # Memory-map all files
        self.mmaps = []
        self.offsets = [0]
        self.total_samples = 0
        for f in self.files:
            arr = np.memmap(f, dtype=np.uint8, mode='r')
            samples = len(arr) // SAMPLE_LENGTH
            self.total_samples += samples
            self.mmaps.append(arr)
            self.offsets.append(self.offsets[-1] + samples)
        print(f"Total samples: {self.total_samples:,}")

    def __len__(self):
        return self.offsets[-1]

    def __getitem__(self, idx):
        # Binary search file
        file_idx = np.searchsorted(self.offsets, idx, side='right') - 1
        local_idx = idx - self.offsets[file_idx]
        offset = local_idx * SAMPLE_LENGTH

        data = self.mmaps[file_idx]
        tokens = torch.from_numpy(data[offset:offset + TOKEN_LENGTH].astype(np.int64))
        action = int(data[offset + TOKEN_LENGTH])

        return tokens, torch.tensor(action, dtype=torch.long)
