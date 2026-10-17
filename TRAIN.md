# Training a JassFormer — cheat sheet

Pipeline: Monte Carlo self-play (Java) → supervised training (imitation of Monte Carlo) → RL fine-tuning (PPO) → ONNX → Java `TransformersStrategy`.

- Java commands run from the **repo root**, Python commands from **`python/model/`**.
- The `rl/` scripts must be run as modules (`python -m rl.…`), otherwise imports like `dataset` fail.
- `TokenExtractor` and `AiTester` show a progress bar; `-v` prints the game logs instead.
- Outputs (data, checkpoints, ONNX files, MLflow and TensorBoard logs) are written to the current directory and are git-ignored.

## 0. Setup

```bash
# Java 21+ JDK (a JRE alone cannot compile), then build the fat jar and run the Java tests
mvn package

# Python environment (in python/model/)
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt                    # GPU machine
pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu   # CPU-only machine (avoids the CUDA wheels)
python -m pytest                                   # 2 tests are skipped without local training_data/ and checkpoint
```

## Steps

| Step | Command | Smoke test values | What to check |
|---|---|---|---|
| 1. Generate data | `java -cp target/jass-*.jar com.leflat.jass.test.TokenExtractor -o python/model/training_data -g 5000 -s 1000` | `-g 15 -s 15`: ~5k samples in ~10 s | One `<run>_seat<i>.dat` per seat, ~350 samples per game. Never overwrites: use `-n <name>` or a new directory. Several runs can share a directory. |
| 2. Check data | `python validate_dataset.py --data training_data/` | | Must finish without error: every recorded action is legal under the Python masks. |
| 3. Supervised training | `python train.py --data training_data/ --epochs 10` | `--epochs 3` (~5 min on CPU) | See *Reading the metrics*. Writes `jassformer_best.pt` (best validation loss) and `jassformer.onnx` (that model, exported). |
| 4. Check checkpoint | see [below](#checking-a-checkpoint) | | `d_model: 256, layers: 6, trump head: True` (RL expects d_model 256) |
| 5. Test in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -g 20 -s 100 python/model/jassformer.onnx` | `-g 3 -s 15` | Plays Monte Carlo (team 0) vs transformers (team 1), shows a progress bar with the running score, then the games won by each team. Exits with an error if the model cannot be loaded. `-v` shows the game logs instead (only the transformers players log their moves). |
| 6. RL fine-tuning | `python -m rl.ppo --supervised-model jassformer_best.pt --total-timesteps 5000000` | `--total-timesteps 6144 --opponent-update-freq 1` (~3.5 min on CPU) | Logs `episodic_return` and losses to `runs/<run>/` (TensorBoard), checkpoints `model_iter_<n>.pth` every `--opponent-update-freq` iterations, and `model_final.pth`. Add `--track` for the MLflow server. |
| 7. Export RL model | `python -m rl.export_checkpoint_to_onnx --rl --checkpoint runs/<run>/model_final.pth --output rl_jass_agent.onnx` | | `✅ Export success!` |
| 8. Test RL model in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -g 20 -s 100 python/model/rl_jass_agent.onnx` | `-g 3 -s 15` | Compare with step 5. |

To ship a model with the client, copy it to `src/main/resources/model/jassformer.onnx` (the default `cp:/model/jassformer.onnx`) and rebuild.

## Checking a checkpoint

Shows the size of a supervised state dict (e.g. `jassformer_best.pt`, or an older `state_dict.pth`):

```bash
python -c "import torch; sd = torch.load('jassformer_best.pt', map_location='cpu'); print('d_model:', sd['embedding.weight'].shape[1], '| layers:', sum(k.endswith('self_attn.in_proj_weight') for k in sd), '| trump head:', 'trump_head.weight' in sd)"
```

## Reading the supervised metrics

Each epoch prints a line like:

```
Val: loss 1.0267 | card 51.21% | unforced card 37.95% (random 29.38%) | trump 64.29% (random 22.50%)
```

- **unforced card** is the number to watch: accuracy on card decisions with more than one legal card, next to what a uniformly random legal move would get on the same decisions. Overall **card** accuracy is inflated by forced moves (~22% of decisions).
- **trump** is reported separately (only ~3% of the samples), also with its random baseline.
- Validation is the last 5% of each data file (`--val_fraction`), i.e. the last games of each run, cut at a round start. When validation loss goes up while training loss keeps going down, the model is overfitting; the best checkpoint is the one with the lowest validation loss.
- Other options: `--d_model` (default 256), `--batch`, `--lr`, `--description`. Runs are logged to MLflow in `mlflow.db`: `mlflow ui --backend-store-uri sqlite:///mlflow.db`.

## RL notes

- Opponents: 95% the frozen supervised model ("teacher"), 5% a snapshot of the agent ("student"), updated every `--opponent-update-freq` iterations. The partner is an opponent model too.
- The first `--warmup-ratio` (20%) of the steps only train the value head; the policy is unchanged during that phase.
- Defaults: 16 parallel games (`--num-envs`), 128 steps each per iteration, 8 minibatches. Opponent moves are batched on the training device.
- Larger batches mean fewer gradient steps per sample: with `--learning-rate 1e-5`, plan for several million timesteps, or raise the learning rate.
- `tensorboard --logdir runs` to follow `charts/episodic_return`, `losses/*` and `charts/SPS`.
