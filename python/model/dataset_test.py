# test_dataset.py
import unittest
import numpy as np
import torch
import os
from dataset import JassBinaryDataset, TOKEN_LENGTH


class TestJassBinaryDataset(unittest.TestCase):
    DATA_DIR = "test_data"
    SAMPLE_FILE = f"{DATA_DIR}/sample.dat"

    @classmethod
    def setUpClass(cls):
        os.makedirs(cls.DATA_DIR, exist_ok=True)
        cls._create_sample_file()

    @classmethod
    def tearDownClass(cls):
        import shutil
        shutil.rmtree(cls.DATA_DIR)

    @classmethod
    def _create_sample_file(cls):
        # 3 perfect samples
        data = bytearray()
        for i in range(3):
            # num_tokens = 99
            data.extend(np.int32(TOKEN_LENGTH).tobytes())
            # tokens: CLS + 8 globals + SECTION_HAND + 9 cards + ... (simplified)
            tokens = list(range(1, 96))
            data.extend(np.array(tokens, dtype=np.uint8).tobytes())
            # action: card token 25 → index 15
            data.extend(np.int32(20 + i).tobytes())
        with open(cls.SAMPLE_FILE, 'wb') as f:
            f.write(data)

    def test_load_correct_number(self):
        dataset = JassBinaryDataset(self.DATA_DIR)
        self.assertEqual(len(dataset), 3)

    def test_first_sample_correct(self):
        dataset = JassBinaryDataset(self.DATA_DIR)
        tokens, action = dataset[0]
        self.assertEqual(tokens.shape, (TOKEN_LENGTH,))
        for i in range(TOKEN_LENGTH):
            self.assertEqual(tokens[i].item(), i+1)        # CLS
        self.assertEqual(action.item(), 20)          # 25 - 10 = 15

    def test_last_sample_correct(self):
        dataset = JassBinaryDataset(self.DATA_DIR)
        tokens, action = dataset[2]
        self.assertEqual(action.item(), 22)

    def test_memory_efficient(self):
        dataset = JassBinaryDataset(self.DATA_DIR)
        # Should not load all into RAM
        self.assertLess(dataset.mmaps[0].nbytes, 500)  # only one file

    def test_invalid_file_skipped_gracefully(self):
        # Create corrupted file
        with open(f"{self.DATA_DIR}/corrupted.dat", 'wb') as f:
            f.write(b"garbage")
        dataset = JassBinaryDataset(self.DATA_DIR)
        self.assertEqual(len(dataset), 3)  # still loads good ones

    def test_different_action_values(self):
        # Create file with pass and different actions
        data = bytearray()
        actions = [10, 20, 45, 30]  # card tokens
        for act in actions:
            data.extend(np.int32(TOKEN_LENGTH).tobytes())
            data.extend(np.array([1] + [0]*(TOKEN_LENGTH-1), dtype=np.uint8).tobytes())
            data.extend(np.int32(act).tobytes())
        with open(f"{self.DATA_DIR}/zactions.dat", 'wb') as f:
            f.write(data)

        dataset = JassBinaryDataset(self.DATA_DIR)
        expected = actions
        for i, exp in enumerate(expected):
            _, action = dataset[3 + i]
            self.assertEqual(action.item(), exp)

        os.remove(f"{self.DATA_DIR}/zactions.dat")