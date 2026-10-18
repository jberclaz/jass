# dataset_test.py
import os
import tempfile
import unittest

import numpy as np

from dataset import JassBinaryDataset, split_train_val, TOKEN_LENGTH, NO_SCORE
from rl.tokens import Tokens


def make_sample(phase: int, hand_size: int, action: int, marker: int = 0) -> bytes:
    """A sample: 96 token bytes followed by the action byte. `marker` is stored in an unused position for tracking."""
    tokens = np.zeros(TOKEN_LENGTH, dtype=np.uint8)
    tokens[0] = Tokens.CLS
    tokens[2] = phase
    tokens[11:11 + hand_size] = [Tokens.card_token(c) for c in range(hand_size)]
    tokens[95] = marker
    return tokens.tobytes() + bytes([action])


def make_scores(scores: dict[int, float]) -> bytes:
    """The 36 scores of a .sdat sample, as written by ArtificialPlayer: big-endian int16 in tenths of a point."""
    raw = np.full(36, NO_SCORE, dtype=">i2")
    for index, score in scores.items():
        raw[index] = round(score * 10)
    return raw.tobytes()


def make_round(round_number: int, choose_trump: bool, scored: bool = False) -> bytes:
    scores = make_scores({0: 1.5}) if scored else b""
    data = b""
    if choose_trump:
        data += make_sample(Tokens.CHOOSE_TRUMP_SUIT, 9, 2, marker=round_number) + scores
    for hand_size in range(9, 0, -1):
        data += make_sample(Tokens.CHOOSE_NEXT_CARD, hand_size, hand_size, marker=round_number) + scores
    return data


class TestJassBinaryDataset(unittest.TestCase):
    def setUp(self):
        self._tmp_dir = tempfile.TemporaryDirectory()
        self.data_dir = self._tmp_dir.name

    def tearDown(self):
        self._tmp_dir.cleanup()

    def _write(self, name: str, data: bytes):
        with open(os.path.join(self.data_dir, name), "wb") as f:
            f.write(data)

    def test_samples(self):
        self._write("a.dat", make_sample(Tokens.CHOOSE_NEXT_CARD, 9, 20) + make_sample(Tokens.CHOOSE_NEXT_CARD, 8, 21))
        self._write("b.dat", make_sample(Tokens.CHOOSE_TRUMP_SUIT, 9, 4))
        dataset = JassBinaryDataset(self.data_dir)
        self.assertEqual(len(dataset), 3)

        tokens, action, scores = dataset[0]
        self.assertEqual(tokens.shape, (TOKEN_LENGTH,))
        self.assertTrue(scores.isnan().all(), ".dat samples have no scores")
        self.assertEqual(tokens[0].item(), Tokens.CLS)
        self.assertEqual(tokens[19].item(), Tokens.card_token(8))
        self.assertEqual(action.item(), 20)
        self.assertEqual(dataset[1][1].item(), 21)

        # sample of the second file
        tokens, action, _ = dataset[2]
        self.assertEqual(tokens[2].item(), Tokens.CHOOSE_TRUMP_SUIT)
        self.assertEqual(action.item(), 4)

    def test_scored_samples(self):
        self._write("a.dat", make_sample(Tokens.CHOOSE_NEXT_CARD, 9, 20))
        self._write("b.sdat", make_sample(Tokens.CHOOSE_NEXT_CARD, 9, 3) + make_scores({3: 12.3, 5: -40.0, 7: 0.0})
                    + make_sample(Tokens.CHOOSE_NEXT_CARD, 8, 21) + make_scores({}))
        dataset = JassBinaryDataset(self.data_dir)
        self.assertEqual(len(dataset), 3)
        self.assertEqual(dataset.scored_samples, 2)

        tokens, action, scores = dataset[1]
        self.assertEqual(tokens[0].item(), Tokens.CLS)
        self.assertEqual(action.item(), 3)
        self.assertAlmostEqual(scores[3].item(), 12.3, places=5)
        self.assertEqual(scores[5].item(), -40.0)
        self.assertEqual(scores[7].item(), 0.0)
        self.assertEqual(scores.isnan().sum().item(), 33)
        # a forced move: no score at all
        tokens, action, scores = dataset[2]
        self.assertEqual(action.item(), 21)
        self.assertTrue(scores.isnan().all())

    def test_incomplete_file_ignored(self):
        self._write("a.dat", make_sample(Tokens.CHOOSE_NEXT_CARD, 9, 20))
        self._write("corrupted.dat", b"garbage")
        dataset = JassBinaryDataset(self.data_dir)
        self.assertEqual(len(dataset), 1)

    def test_split_at_round_start(self):
        # 2 files of 20 rounds, in both formats, the player sometimes chooses trump
        for name in ["seat0.dat", "seat1.sdat"]:
            self._write(name, b"".join(make_round(r, choose_trump=(r % 3 == 0), scored=name.endswith(".sdat"))
                                       for r in range(20)))
        dataset = JassBinaryDataset(self.data_dir)
        train_set, val_set = split_train_val(dataset, 0.1)

        train_indices, val_indices = set(train_set.indices), set(val_set.indices)
        self.assertFalse(train_indices & val_indices)
        self.assertEqual(len(train_indices) + len(val_indices), len(dataset))
        self.assertGreaterEqual(len(val_indices), 0.08 * len(dataset))

        # no round is shared between training and validation, and validation holds the last rounds of each file
        for file_idx in range(2):
            first, end = dataset.offsets[file_idx], dataset.offsets[file_idx + 1]
            train_rounds = {dataset[i][0][95].item() for i in range(first, end) if i in train_indices}
            val_rounds = {dataset[i][0][95].item() for i in range(first, end) if i in val_indices}
            self.assertFalse(train_rounds & val_rounds)
            self.assertGreater(min(val_rounds), max(train_rounds))

    @unittest.skipUnless(os.path.isdir("training_data"), "no local training data")
    def test_real_dataset(self):
        dataset = JassBinaryDataset("training_data/")
        self.assertGreater(len(dataset), 0)
        for i in range(min(100, len(dataset))):
            tokens, action, _ = dataset[i]
            self.assertEqual(tokens[0].item(), Tokens.CLS)
            self.assertIn(tokens[2].item(), [Tokens.CHOOSE_NEXT_CARD, Tokens.CHOOSE_TRUMP_SUIT])


if __name__ == "__main__":
    unittest.main()
