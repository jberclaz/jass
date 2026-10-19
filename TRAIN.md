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
| 5. Test in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -n 1000 -s 100,300 python/model/jassformer.onnx` | `-n 20 -s 15` | Plays transformers vs Monte Carlo at each strength: single hands, on pairs with the same deals (see [Evaluating](#evaluating-against-monte-carlo)). Shows a progress bar, then the point difference per hand and the share of hands won, with their 95% confidence intervals. Exits with an error if the model cannot be loaded. `-v` shows the game logs instead (only the transformers players log their moves); `-r <file>` also writes the results as JSON. Or `python -m rl.evaluate jassformer_best.pt --strengths 100 300`, see [Evaluating](#evaluating-against-monte-carlo). |
| 6. RL fine-tuning | `python -m rl.ppo --supervised-model jassformer_best.pt --total-timesteps 10000000 --num-envs 64 --num-steps 256 --opponent-update-freq 15 --track` | `--total-timesteps 6144 --warmup-steps 2048 --opponent-update-freq 1` (~3.5 min on CPU) | Prints one line per iteration (SPS, warmup, points per hand and hands won against the opponents, value loss, KL). Run `rl.evaluate --watch` next to it (see [Evaluating](#evaluating-against-monte-carlo)). Logs to `runs/<run>/` (TensorBoard), checkpoints `model_iter_<n>.pth` every `--opponent-update-freq` iterations, and `model_final.pth` + `model_final.onnx`. `--track` also logs to MLflow, see [RL notes](#rl-notes). |
| 7. Export RL model | `python -m rl.export_checkpoint_to_onnx --rl --checkpoint runs/<run>/model_iter_<n>.pth --output rl_jass_agent.onnx` | | `✅ Export success!` Only needed for a checkpoint: the final model is already exported (`model_final.onnx`). |
| 8. Test RL model in Java | `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -n 1000 -s 100,300 python/model/rl_jass_agent.onnx` | `-n 20 -s 15` | Compare with step 5. Steps 7–8 in one go, during or after training: `python -m rl.evaluate --watch runs/<run>`. |

### Shipping a model with the client

The jar bundles the model as `/model/jassformer.onnx` (the default `cp:/model/jassformer.onnx`), which the client's AI players use. It is not in the repository: Maven downloads it from a GitHub release (`jassformer.model.url` in `pom.xml`) and checks its SHA-256 (`jassformer.model.sha256`). To ship a new model:

1. Export it with its provenance, from `python/model/`:
   `python -m rl.export_checkpoint_to_onnx --rl --checkpoint <model_final.pth> --output jassformer.onnx --mlflow-run <MLflow run id>`.
   This stores in the ONNX metadata the checkpoint (name and SHA-256), the MLflow run (id, name, experiment), the git commit it was trained from and its latest `eval/*` metrics. Java logs them when it loads the model (`Model metadata: ...`).
2. Publish it: `gh release create jassformer-v<n> jassformer.onnx --title "JassFormer v<n>" --notes "<the run and its evaluation>"`.
3. In `pom.xml`, update the URL, `sha256sum jassformer.onnx` and the comment saying where the model comes from, then rebuild.

To try a model without publishing it, override the URL: `mvn package -Djassformer.model.url=<url>` with the matching `-Djassformer.model.sha256=<hash>`. The downloads are cached in `~/.m2/repository/.cache/download-maven-plugin`.

## Evaluating against Monte Carlo

`rl.evaluate` exports checkpoints to ONNX and plays them against Monte Carlo with AiTester (build the jar first). It accepts supervised (`.pt`) and RL (`.pth`) state dicts, and finds `d_model` itself.

```bash
# fixed checkpoints, e.g. the supervised base and an RL model
python -m rl.evaluate jassformer_best.pt runs/<run>/model_final.pth --hands 1000 --strengths 100 300

# head to head against other models (and/or Monte Carlo with --strengths)
python -m rl.evaluate runs/<run>/model_final.pth --opponents jassformer_best.pt runs/<other run>/model_final.pth --hands 2000

# a PPO run, in a second terminal: evaluates each model_iter_<n>.pth as it is written (newest first), until the final model
python -m rl.evaluate --watch runs/<run> --hands 1000 --strengths 100
```

- **Same deals for every model**: `rl.evaluate` uses `--seed 1` by default, so the baseline and all the checkpoints are played on the same deals and their differences do not depend on the cards (Monte Carlo still plays randomly). Compare results with the same `--hands`: deal k is the same in every run, so a larger `--hands` adds deals to the same first ones. `--seed None` for random deals.
- Results are appended to `eval.jsonl` (in the run directory with `--watch`). With `--watch`, they are also logged to the run's TensorBoard as `eval/points_per_hand_mc<strength>` and `eval/hand_win_rate_mc<strength>`, on the same `global_step` axis as the training charts.
- **Hands, not games** (default): each hand is played on its own, from a score of 0 - 0. Hands are independent; a game to 1500 points adds up the luck of several hands and then keeps only who crossed the line first. `-g <n>` (`--games` in `rl.evaluate`) plays full games instead.
- **Duplicate deals** (default): each deal is played twice, with the model in either team, so both sides get the same cards and most of the luck cancels out. `--no-duplicate` uses independent deals; `--seed <n>` replays the same deals, e.g. to compare two models on identical hands.
- **Points per hand** (model minus Monte Carlo, including announcements, last trick and match) is the main number; the share of hands won is shown next to it.
- Measured on MC-10 vs MC-10 over 400 hands: +2.8 [−3.9, +9.5] points/hand with duplicate deals, ±14 with independent deals, i.e. about 4 times fewer hands for the same precision. The interval shrinks with the square root of the number of hands: 1000 hands give about ±4 points/hand.
- The players always see a score of 0 - 0, so play near the end of a game (close to 1500) is not tested; it changes the strategy little.
- Monte Carlo uses all CPU cores, so evaluating slows down a PPO run on the same machine (and vice versa). With `--watch`, `--opponent-update-freq` sets how often checkpoints appear; if evaluation can't keep up, the newest checkpoint is evaluated first.
- **Head to head** (`--opponents`, `-o <model>` in AiTester): the models play deterministically (the best card), so only the choice of the deals is random, and the same model against itself gives exactly 0. About 3–4 times faster than against MC-100 (the ONNX inference is the bottleneck). Opponents can be `.pt`, `.pth`, `.onnx` or `mc:<strength>`; with `--watch`, they are logged as `eval/points_per_hand_vs_<path>`. A model is flattered against the one it was trained against (an RL model against its teacher): Monte Carlo stays the reference to compare runs.
- Evaluate the supervised base model at the same strengths first: that's the baseline RL has to beat.
- To see how much better the teacher is (the ceiling of supervised training), play Monte Carlo against Monte Carlo: `java -cp target/jass-*.jar com.leflat.jass.test.AiTester -n 1000 -s 100 mc:1000` (the model side is MonteCarlo-1000). Slow: MC-1000 takes ~10 times longer per move than MC-100.

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

- **Opponents**: for each hand, one model plays the 3 other players (partner included), so that their moves stay batched:
  - a **fixed opponent**: the supervised model, or the models of `--opponent-models` (`.pt` or `.pth`, e.g. older bases or the final model of a previous run);
  - or, in `1 - --fixed-opponent-fraction` (50%) of the hands, a **snapshot of the agent** from a pool of its last `--pool-size` (8) checkpoints, one added every `--opponent-update-freq` iterations. One snapshot is drawn per iteration rather than per hand: the opponents' moves are batched per model, and with several snapshots at a time the steps per second dropped by almost half. The pool starts empty: until the first checkpoint, all hands are against fixed opponents. `--pool-size 0` plays the fixed opponents only.
  - Against a single fixed teacher, the agent ends up learning to beat that teacher more than to play well (a run with a 95% teacher reached +8.7 points/hand against it while its gain against MC-100 had stalled); snapshots keep raising the level of the opponents.
  - `opponents/points_per_hand_vs_<name>` (and `win_rate_vs_`, `hands_vs_`): results against each fixed opponent (named after its file) and against the pool, also on the console line. Gaining against the pool as well as against the fixed opponents means the agent really gets stronger.
- **Continuing a run**: `--init-checkpoint runs/<run>/model_final.pth` starts from an RL checkpoint, with its trained value head; the warmup then defaults to 0. Give the previous model as a fixed opponent too, e.g. `--opponent-models jassformer_best.pt runs/<run>/model_final.pth`.
- **Episodes are single hands** from 0 - 0 (`--no-hand-episodes` for games to 1500). Each step is rewarded with the change of the point difference / 157 (announcements, last trick and match included), so the return of a hand is its point difference / 157, the same quantity as the evaluation. Rewarding games (±10 at the end) mostly rewards the luck of the following hands, which the value head cannot predict.
- The first `--warmup-steps` (200k, 0 with `--init-checkpoint`) only train the value head; the policy is unchanged during that phase. The explained variance stops rising after ~200k steps, whatever the length of the run.
- **Batch size**: an update uses `--num-envs` × `--num-steps` steps (about 10 steps per hand). With 16 × 128 (~200 hands), the luck of the cards dominates the gradient: the policy drifts (`approx_kl` 0.03 per update) without improving, and its strength against Monte Carlo swings by 4–5 points/hand between checkpoints. 64 × 256 (~1,600 hands) gives a much more reliable direction, and batches the opponents' inference better.
- **Learning rates**: `--learning-rate` (3e-5) for the policy (transformer and card/trump heads); the value head starts from random weights and has its own: `--warmup-value-learning-rate` (1e-3) during the warmup, `--value-learning-rate` (1e-4) after. Value clipping is off (`--clip-vloss` to enable). `--target-kl` (0.01) stops an update at the first minibatch where the policy has moved too much from the rollout policy (`losses/minibatch_updates` shows how many of the `--update-epochs` × `--num-minibatches` were applied). `--gamma` defaults to 1 with hand episodes: a hand is short and always ends, and discounting would undervalue the last trick and the match bonus.
- Defaults: 16 parallel games (`--num-envs`), 128 steps each per iteration, 8 minibatches (use 64 × 256 for real runs, see above). Opponent moves are batched on the training device. About 770 steps/s on the GPU machine: 1M steps take ~22 min.
- What to watch: `losses/explained_variance` should rise during the warmup (a hand depends a lot on the hidden cards: 0.1–0.3 is already useful); after it, `losses/approx_kl` around 0.005–0.02 and `losses/entropy` not collapsing. `charts/points_per_hand` is against all the opponents, `opponents/*` by opponent; `eval/points_per_hand_mc100` is the real measure.
- `tensorboard --logdir runs` to follow `charts/*`, `losses/*` and, with `rl.evaluate --watch`, `eval/*`.
- **MLflow** (`--track`): same metrics, in experiment `Jass_SelfPlay_PPO` (`--mlflow-experiment`), run named like the `runs/` directory. Server: `--mlflow-tracking-uri`, else `$MLFLOW_TRACKING_URI`, else `http://192.168.11.98:5000`. Uploads `model_final.pth` and `model_final.onnx`; `--mlflow-log-checkpoints` also uploads every `model_iter_<n>.pth` (~20 MB each). `rl.evaluate --watch` logs its `eval/*` results to the same MLflow run (the run id is in `runs/<run>/args.json`).
- The game logs of the Python environment are at `DEBUG` level (`logging`), off by default.
