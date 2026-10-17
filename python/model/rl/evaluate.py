"""
Evaluates models against MonteCarlo players, by exporting them to ONNX and playing them in Java (AiTester).

    # one or several checkpoints (supervised .pt or RL .pth, detected automatically)
    python -m rl.evaluate jassformer_best.pt runs/<run>/model_final.pth --games 200 --strengths 100 300

    # every checkpoint of a PPO run, as they are written; the win rates are logged to the run's TensorBoard
    python -m rl.evaluate --watch runs/<run> --games 200 --strengths 100

The results are appended to eval.jsonl, in the run directory with --watch, in the current directory otherwise.
Runs one game at a time; MonteCarlo already uses all the CPU cores.
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
    games: int = 200
    """games per MonteCarlo strength"""
    strengths: list[int] = field(default_factory=lambda: [100])
    """MonteCarlo strengths to play against"""
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


def evaluate(checkpoint: str, args: Args, jar: str) -> list[dict]:
    """Returns one result per strength: {strength, games, wins, win_rate, ci_low, ci_high}."""
    with tempfile.TemporaryDirectory() as tmp:
        onnx_path = os.path.join(tmp, "model.onnx")
        results_path = os.path.join(tmp, "results.json")
        export_to_onnx(checkpoint, onnx_path)
        command = ["java", "--enable-native-access=ALL-UNNAMED", "-cp", jar, "com.leflat.jass.test.AiTester",
                   "-g", str(args.games), "-s", ",".join(map(str, args.strengths)), "-r", results_path, onnx_path]
        sys.stdout.flush()  # keeps our output in order with Java's when piped
        subprocess.run(command, check=True)
        with open(results_path) as f:
            return json.load(f)["results"]


def append_results(path: str, checkpoint: str, results: list[dict], global_step: Optional[int] = None):
    with open(path, "a") as f:
        for r in results:
            f.write(json.dumps({"checkpoint": checkpoint, "global_step": global_step, **r}) + "\n")


def evaluated_checkpoints(path: str) -> set[str]:
    if not os.path.exists(path):
        return set()
    with open(path) as f:
        return {json.loads(line)["checkpoint"] for line in f if line.strip()}


def print_results(checkpoint: str, results: list[dict]):
    for r in results:
        print(f"{checkpoint} vs MonteCarlo-{r['strength']}: {r['win_rate']:.1%} "
              f"[{r['ci_low']:.1%}, {r['ci_high']:.1%}] over {r['games']} games")


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
            append_results(eval_log, os.path.basename(checkpoint), results, step)
            for r in results:
                writer.add_scalar(f"eval/win_rate_mc{r['strength']}", r["win_rate"], step)
            writer.flush()
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
        append_results("eval.jsonl", checkpoint, all_results[checkpoint])
    print()
    for checkpoint, results in all_results.items():
        print_results(checkpoint, results)


if __name__ == "__main__":
    main()
