"""
Evaluates models against MonteCarlo players, by exporting them to ONNX and playing them in Java (AiTester).

    # one or several checkpoints (supervised .pt or RL .pth, detected automatically)
    python -m rl.evaluate jassformer_best.pt runs/<run>/model_final.pth --hands 1000 --strengths 100 300

    # against other models (head to head, on the same deals), with or without MonteCarlo
    python -m rl.evaluate runs/<run>/model_final.pth --opponents jassformer_best.pt runs/<other run>/model_final.pth

    # every checkpoint of a PPO run, as they are written; the results are logged to the run's TensorBoard, and to its
    # MLflow run when PPO was started with --track
    python -m rl.evaluate --watch runs/<run> --hands 1000 --strengths 100

The results are appended to eval.jsonl, in the run directory with --watch, in the current directory otherwise.
Runs one game at a time; MonteCarlo and the ONNX inference already use all the CPU cores.

Head-to-head results between models are faster and more precise than against MonteCarlo (the models play
deterministically, so only the choice of the deals is random), but a model trained against another one is flattered
against it: MonteCarlo stays the reference.
"""
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import torch
import tyro

from rl.export_checkpoint_to_onnx import export_rl_model_to_onnx, export_supervised_model_to_onnx

REPO_ROOT = Path(__file__).resolve().parents[3]


@dataclass
class Args:
    checkpoints: tyro.conf.Positional[list[str]] = field(default_factory=list)
    """supervised (.pt) or RL (.pth) state dicts to evaluate"""
    watch: Optional[str] = None
    """a PPO run directory (runs/<run>): evaluates each model_iter_<n>.pth as it appears, until the final model"""
    hands: int = 1000
    """hands per MonteCarlo strength, each from a score of 0 - 0 (played as pairs of hands with the same deals)"""
    games: Optional[int] = None
    """play this number of full games to 1500 points instead of hands"""
    strengths: Optional[list[int]] = None
    """MonteCarlo strengths to play against (default: 100, unless there are --opponents)"""
    opponents: list[str] = field(default_factory=list)
    """other models to play against: supervised (.pt) or RL (.pth) state dicts, ONNX models, or mc:<strength>"""
    seed: Optional[int] = 1
    """seed of the deals: with the same seed, all the models are evaluated on the same deals, so that the differences
    between them do not depend on the cards (MonteCarlo still plays randomly). None for random deals"""
    jar: Optional[str] = None
    """the jass fat jar (default: the newest target/jass-*.jar of the repository)"""
    poll_seconds: int = 60
    """with --watch, how often to look for new checkpoints"""


def find_jar(jar: Optional[str]) -> str:
    if jar:
        return jar
    jars = sorted(glob.glob(str(REPO_ROOT / "target" / "jass-*.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit(f"No jar found in {REPO_ROOT / 'target'}: build it with `mvn package -DskipTests`")
    return jars[-1]


def export_to_onnx(checkpoint: str, output: str):
    state_dict = torch.load(checkpoint, map_location="cpu")
    d_model = state_dict["embedding.weight"].shape[1]
    if "value_head.weight" in state_dict:
        export_rl_model_to_onnx(checkpoint, output, d_model)
    else:
        export_supervised_model_to_onnx(checkpoint, output, d_model)


def opponent_name(opponent: str) -> str:
    """MonteCarlo-<strength>, or the checkpoint path (relative paths as given)."""
    return f"MonteCarlo-{opponent[3:]}" if opponent.startswith("mc:") else opponent


def to_onnx(model: str, onnx_path: str) -> str:
    """The ONNX model to give to AiTester: model itself if it is already ONNX, else its export to onnx_path."""
    if model.endswith(".onnx"):
        return model
    export_to_onnx(model, onnx_path)
    return onnx_path


def evaluate(checkpoint: str, args: Args, jar: str) -> list[dict]:
    """
    Returns one result per opponent (MonteCarlo strengths first): {opponent, strength (None for a model), unit,
    duplicate, count, wins, win_rate, ci_low, ci_high, points, points_ci_low, points_ci_high}, points being the point
    difference per hand (or game): model - opponent.
    """
    strengths = args.strengths if args.strengths is not None else [] if args.opponents else [100]
    with tempfile.TemporaryDirectory() as tmp:
        results_path = os.path.join(tmp, "results.json")
        used_names = set()

        def onnx_file(model: str) -> str:
            # named after the model, for AiTester's output; numbered when several models have the same name
            # (model_final.pth of different runs)
            stem = os.path.splitext(os.path.basename(model))[0]
            name, i = stem, 1
            while name in used_names:
                i += 1
                name = f"{stem}_{i}"
            used_names.add(name)
            return os.path.join(tmp, f"{name}.onnx")

        command = ["java", "--enable-native-access=ALL-UNNAMED", "-cp", jar, "com.leflat.jass.test.AiTester",
                   *(["-g", str(args.games)] if args.games else ["-n", str(args.hands)]),
                   *(["--seed", str(args.seed)] if args.seed is not None else []),
                   *(["-s", ",".join(map(str, strengths))] if strengths else []), "-r", results_path]
        candidate = to_onnx(checkpoint, onnx_file(checkpoint))
        for opponent in args.opponents:
            command += ["-o", opponent if opponent.startswith("mc:") else to_onnx(opponent, onnx_file(opponent))]
        command.append(candidate)
        sys.stdout.flush()  # keeps our output in order with Java's when piped
        subprocess.run(command, check=True)
        with open(results_path) as f:
            results = json.load(f)["results"]
    # AiTester only knows the temporary ONNX files: names the opponents as given
    names = [f"mc:{s}" for s in strengths] + args.opponents
    for r, opponent in zip(results, names):
        r["opponent"] = opponent
        r.pop("opponent_name", None)
    return results


def append_results(path: str, checkpoint: str, results: list[dict], seed: Optional[int],
                   global_step: Optional[int] = None):
    with open(path, "a") as f:
        for r in results:
            f.write(json.dumps({"checkpoint": checkpoint, "global_step": global_step, "seed": seed, **r}) + "\n")


def evaluated_checkpoints(path: str) -> set[str]:
    if not os.path.exists(path):
        return set()
    with open(path) as f:
        return {json.loads(line)["checkpoint"] for line in f if line.strip()}


def print_results(checkpoint: str, results: list[dict]):
    for r in results:
        opponent = r.get("opponent") or f"mc:{r['strength']}"  # older results have no opponent
        print(f"{checkpoint} vs {opponent_name(opponent)}: {r['points']:+.1f} "
              f"[{r['points_ci_low']:+.1f}, {r['points_ci_high']:+.1f}] points/{r['unit']} | won {r['win_rate']:.1%} "
              f"[{r['ci_low']:.1%}, {r['ci_high']:.1%}] | {r['count']} {r['unit']}s")


def metric_name(model: str) -> str:
    """vs_<path without extension, as given>, with the characters that TensorBoard and MLflow accept."""
    return "vs_" + re.sub(r"[^A-Za-z0-9_.-]", "_", os.path.splitext(model)[0])


def checkpoint_step(path: str, run_args: dict) -> Optional[int]:
    """The global_step at which PPO saved this checkpoint."""
    name = os.path.basename(path)
    match = re.fullmatch(r"model_iter_(\d+)\.pth", name)
    if match:
        return int(match.group(1)) * run_args["batch_size"]
    if name.endswith("final.pth"):
        return run_args["num_iterations"] * run_args["batch_size"]
    return None


def watch(args: Args, jar: str):
    from torch.utils.tensorboard import SummaryWriter

    run_dir = args.watch
    with open(os.path.join(run_dir, "args.json")) as f:
        run_args = json.load(f)
    eval_log = os.path.join(run_dir, "eval.jsonl")
    writer = SummaryWriter(run_dir)
    mlflow_client = None
    if run_args.get("mlflow_run_id"):
        from mlflow import MlflowClient
        mlflow_client = MlflowClient(tracking_uri=run_args["mlflow_tracking_uri"])
        print(f"Logging to MLflow run {run_args['mlflow_run_id']} at {run_args['mlflow_tracking_uri']}")
    while True:
        done = evaluated_checkpoints(eval_log)
        last_iteration = os.path.join(run_dir, f"model_iter_{run_args['num_iterations']}.pth")
        pending = [p for p in glob.glob(os.path.join(run_dir, "model_*.pth"))
                   if checkpoint_step(p, run_args) is not None and os.path.basename(p) not in done
                   # the final model is the same as the checkpoint of the last iteration, when there is one
                   and not (p.endswith("final.pth") and os.path.exists(last_iteration))]
        # the newest first: with slow evaluations, the latest model matters more than the skipped ones
        pending.sort(key=lambda p: checkpoint_step(p, run_args), reverse=True)
        if pending:
            checkpoint = pending[0]
            time.sleep(2)  # lets torch.save finish writing the file
            step = checkpoint_step(checkpoint, run_args)
            results = evaluate(checkpoint, args, jar)
            print_results(checkpoint, results)
            append_results(eval_log, os.path.basename(checkpoint), results, args.seed, step)
            metrics = {}
            for r in results:
                suffix = f"mc{r['strength']}" if r["strength"] is not None else metric_name(r["opponent"])
                metrics[f"eval/points_per_{r['unit']}_{suffix}"] = r["points"]
                metrics[f"eval/{r['unit']}_win_rate_{suffix}"] = r["win_rate"]
            for key, value in metrics.items():
                writer.add_scalar(key, value, step)
            writer.flush()
            if mlflow_client:
                try:
                    for key, value in metrics.items():
                        mlflow_client.log_metric(run_args["mlflow_run_id"], key, value, step=step)
                except Exception as e:  # a server problem must not stop the evaluations
                    print(f"Unable to log to MLflow: {e}")
            continue
        if any(p.endswith("final.pth") for p in glob.glob(os.path.join(run_dir, "model_*.pth"))):
            break  # the run is over and all its checkpoints have been evaluated
        time.sleep(args.poll_seconds)
    writer.close()


def main():
    args = tyro.cli(Args)
    jar = find_jar(args.jar)
    if args.watch:
        watch(args, jar)
        return
    if not args.checkpoints:
        sys.exit("Give checkpoints to evaluate, or --watch <run directory>")
    all_results = {}
    for checkpoint in args.checkpoints:
        all_results[checkpoint] = evaluate(checkpoint, args, jar)
        append_results("eval.jsonl", checkpoint, all_results[checkpoint], args.seed)
    print()
    for checkpoint, results in all_results.items():
        print_results(checkpoint, results)


if __name__ == "__main__":
    main()
