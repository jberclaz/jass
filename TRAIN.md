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
| 5. Test in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -g 200 -s 100,300 python/model/jassformer.onnx` | `-g 3 -s 15` | Plays transformers vs Monte Carlo at each strength, on pairs of games with the same deals (see [Evaluating](#evaluating-against-monte-carlo)), shows a progress bar with the running score, then the win rate and the point difference per game, with their 95% confidence intervals. Exits with an error if the model cannot be loaded. `-v` shows the game logs instead (only the transformers players log their moves); `-r <file>` also writes the results as JSON. Or `python -m rl.evaluate jassformer_best.pt --strengths 100 300`, see [Evaluating](#evaluating-against-monte-carlo). |
| 6. RL fine-tuning | `python -m rl.ppo --supervised-model jassformer_best.pt --total-timesteps 5000000` | `--total-timesteps 6144 --opponent-update-freq 1` (~3.5 min on CPU) | Logs `episodic_return` and losses to `runs/<run>/` (TensorBoard), checkpoints `model_iter_<n>.pth` every `--opponent-update-freq` iterations, and `model_final.pth`. Add `--track` for the MLflow server. |
| 7. Export RL model | `python -m rl.export_checkpoint_to_onnx --rl --checkpoint runs/<run>/model_final.pth --output rl_jass_agent.onnx` | | `✅ Export success!` |
| 8. Test RL model in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -g 200 -s 100,300 python/model/rl_jass_agent.onnx` | `-g 3 -s 15` | Compare with step 5. Steps 7–8 in one go, during or after training: `python -m rl.evaluate --watch runs/<run>`. |

To ship a model with the client, copy it to `src/main/resources/model/jassformer.onnx` (the default `cp:/model/jassformer.onnx`) and rebuild.

## Evaluating against Monte Carlo

`rl.evaluate` exports checkpoints to ONNX and plays them against Monte Carlo with AiTester (build the jar first). It accepts supervised (`.pt`) and RL (`.pth`) state dicts, and finds `d_model` itself.

```bash
# fixed checkpoints, e.g. the supervised base and an RL model
python -m rl.evaluate jassformer_best.pt runs/<run>/model_final.pth --games 200 --strengths 100 300

# a PPO run, in a second terminal: evaluates each model_iter_<n>.pth as it is written (newest first), until the final model
python -m rl.evaluate --watch runs/<run> --games 200 --strengths 100
```

- Results are appended to `eval.jsonl` (in the run directory with `--watch`). With `--watch`, they are also logged to the run's TensorBoard as `eval/win_rate_mc<strength>` and `eval/points_mc<strength>`, on the same `global_step` axis as the training charts.
- **Duplicate deals** (default): each deal is played twice, with the model in either team, so both sides get the same cards and most of the luck cancels out. Measured on MC-10 vs MC-10 over 60 games: win-rate interval ±6 points instead of ±12 with independent deals (`--no-duplicate`), i.e. about 4 times fewer games for the same precision. `--seed <n>` replays the same deals, e.g. to compare two models on identical games.
- **Points per game** is the model's score minus Monte Carlo's at the end of the game (games go to 1500). It moves before the win rate does, and is the better number to compare close models.
- 200 games (100 deal pairs) give a win-rate interval of about ±3–5 points around 50%, instead of ±7 with independent deals.
- Monte Carlo uses all CPU cores, so evaluating slows down a PPO run on the same machine (and vice versa). With `--watch`, `--opponent-update-freq` sets how often checkpoints appear; if evaluation can't keep up, the newest checkpoint is evaluated first.
- Evaluate the supervised base model at the same strengths first: that's the baseline RL has to beat.
- To see how much better the teacher is (the ceiling of supervised training), play Monte Carlo against Monte Carlo: `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -g 200 -s 100 mc:1000` (the model side is MonteCarlo-1000). Slow: MC-1000 takes ~10 times longer per move than MC-100.

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
