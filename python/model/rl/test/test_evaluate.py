import unittest

from rl.evaluate import checkpoint_step


class TestCheckpointStep(unittest.TestCase):
    def test_checkpoint_step(self):
        run_args = {"batch_size": 100, "num_iterations": 7}
        self.assertEqual(checkpoint_step("runs/r/model_iter_3.pth", run_args), 300)
        self.assertEqual(checkpoint_step("runs/r/model_iter_3_ema.pth", run_args), 300)
        self.assertEqual(checkpoint_step("runs/r/model_final.pth", run_args), 700)
        self.assertEqual(checkpoint_step("runs/r/model_final_ema.pth", run_args), 700)
        self.assertIsNone(checkpoint_step("runs/r/model_iter_3_other.pth", run_args))
        self.assertIsNone(checkpoint_step("runs/r/other_final.pth", run_args))
