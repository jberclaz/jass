# docs and experiment results can be found at https://docs.cleanrl.dev/rl-algorithms/ppo/#ppopy
import json
import os
import random
import re
import subprocess
import time
from dataclasses import dataclass, field
from typing import Optional

import mlflow
import gymnasium as gym
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
import tyro
from torch.utils.tensorboard import SummaryWriter

from model import JassFormerActorCritic
from rl.export_checkpoint_to_onnx import export_rl_model_to_onnx
from rl.jass_env import JASSFORMER_MODEL_PATH
from rl.jass_vec_env import JassVecEnv

DEFAULT_MLFLOW_TRACKING_URI = "http://192.168.11.98:5000"


@dataclass
class Args:
    exp_name: str = os.path.basename(__file__)[: -len(".py")]
    """the name of this experiment"""
    seed: int = 1
    """seed of the experiment"""
    torch_deterministic: bool = True
    """if toggled, `torch.backends.cudnn.deterministic=False`"""
    cuda: bool = True
    """if toggled, cuda will be enabled by default"""
    track: bool = False
    """if toggled, also log the run to MLflow (TensorBoard logging in runs/<run> is always on)"""
    mlflow_tracking_uri: Optional[str] = None
    """MLflow server (default: $MLFLOW_TRACKING_URI, else http://192.168.11.98:5000)"""
    mlflow_experiment: str = "Jass_SelfPlay_PPO"
    """MLflow experiment"""
    mlflow_log_checkpoints: bool = False
    """with --track, also upload every model_iter_<n>.pth to MLflow (the final model is always uploaded)"""

    # Algorithm specific arguments
    env_id: str = "JassFormer-v1"
    """the id of the environment"""
    supervised_model: str = JASSFORMER_MODEL_PATH
    """state dict of the supervised JassFormer, used to initialize the agent (without --init-checkpoint) and as the
    fixed opponent (without --opponent-models)"""
    init_checkpoint: Optional[str] = None
    """RL checkpoint (.pth, e.g. model_final.pth of a previous run) to continue from: the agent starts with its policy
    and its trained value head, and the warmup defaults to 0"""
    opponent_models: list[str] = field(default_factory=list)
    """fixed opponents: supervised (.pt) or RL (.pth) state dicts (default: --supervised-model)"""
    pool_size: int = 8
    """number of the agent's last snapshots (one every --opponent-update-freq iterations) kept as opponents, one of
    them drawn for each iteration; 0 for none"""
    fixed_opponent_fraction: float = 0.5
    """share of the games against a fixed opponent, the others against a snapshot of the pool (once it has one)"""
    total_timesteps: int = 500000
    """total timesteps of the experiments"""
    learning_rate: float = 3e-5
    """the learning rate of the policy (the transformer and the card and trump heads)"""
    value_learning_rate: float = 1e-4
    """the learning rate of the value head, after the warmup"""
    warmup_value_learning_rate: float = 1e-3
    """the learning rate of the value head during the warmup (it starts from random weights)"""
    hand_episodes: bool = True
    """episodes are single hands from 0 - 0, rewarded with their point difference / 157; otherwise games to 1500
    points, rewarded +-10 at the end (plus the point difference of each hand and a bonus per trick)"""
    num_envs: int = 16
    """the number of parallel game environments"""
    num_steps: int = 128
    """the number of steps to run in each environment per policy rollout"""
    anneal_lr: bool = True
    """Toggle learning rate annealing for policy and value networks"""
    gamma: Optional[float] = None
    """the discount factor gamma (default: 1 with hand episodes, which are short and always end; 0.99 otherwise)"""
    gae_lambda: float = 0.95
    """the lambda for the general advantage estimation"""
    num_minibatches: int = 8
    """the number of mini-batches"""
    update_epochs: int = 4
    """the K epochs to update the policy"""
    norm_adv: bool = True
    """Toggles advantages normalization"""
    clip_coef: float = 0.2
    """the surrogate clipping coefficient"""
    clip_vloss: bool = False
    """Toggles whether or not to use a clipped loss for the value function, as per the paper."""
    ent_coef: float = 0.01
    """coefficient of the entropy"""
    vf_coef: float = 0.5
    """coefficient of the value function"""
    max_grad_norm: float = 0.5
    """the maximum norm for the gradient clipping"""
    target_kl: Optional[float] = 0.01
    """the update stops at the first minibatch where the KL divergence from the rollout policy exceeds this (None: never)"""
    warmup_steps: Optional[int] = None
    """number of steps (at the start) that only train the value function, the policy being frozen (default: 200k,
    0 with --init-checkpoint)"""

    # to be filled in runtime
    batch_size: int = 0
    """the batch size (computed in runtime)"""
    minibatch_size: int = 0
    """the mini-batch size (computed in runtime)"""
    num_iterations: int = 0
    """the number of iterations (computed in runtime)"""
    opponent_update_freq: int = 10
    """number of iterations between checkpoints (model_iter_<n>.pth), each also added to the pool of opponents"""


def git_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], stderr=subprocess.DEVNULL,
                                       cwd=os.path.dirname(os.path.abspath(__file__))).decode().strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


if __name__ == "__main__":
    args = tyro.cli(Args)
    args.batch_size = int(args.num_envs * args.num_steps)
    args.minibatch_size = int(args.batch_size // args.num_minibatches)
    args.num_iterations = args.total_timesteps // args.batch_size
    if args.gamma is None:
        args.gamma = 1.0 if args.hand_episodes else 0.99
    if args.warmup_steps is None:
        args.warmup_steps = 0 if args.init_checkpoint else 200_000
    run_name = f"{args.env_id}__{args.exp_name}__{args.seed}__{int(time.time())}"
    run_dir = f"runs/{run_name}"
    run_info = {}
    if args.track:
        args.mlflow_tracking_uri = (args.mlflow_tracking_uri or os.environ.get("MLFLOW_TRACKING_URI")
                                    or DEFAULT_MLFLOW_TRACKING_URI)
        mlflow.set_tracking_uri(args.mlflow_tracking_uri)
        mlflow.set_experiment(args.mlflow_experiment)
        mlflow_run = mlflow.start_run(run_name=run_name)
        mlflow.log_params(vars(args))
        mlflow.set_tags({"git_commit": git_commit(), "run_dir": os.path.abspath(run_dir)})
        run_info = {"mlflow_run_id": mlflow_run.info.run_id}
        print(f"MLflow run {mlflow_run.info.run_id} in experiment {args.mlflow_experiment} at {args.mlflow_tracking_uri}")

    writer = SummaryWriter(run_dir)
    writer.add_text(
        "hyperparameters",
        "|param|value|\n|-|-|\n%s" % ("\n".join([f"|{key}|{value}|" for key, value in vars(args).items()])),
    )
    # read by rl.evaluate, to place the checkpoints (model_iter_<n>.pth) on the global_step axis, and to log its
    # results to the same MLflow run
    with open(os.path.join(run_dir, "args.json"), "w") as f:
        json.dump(vars(args) | run_info, f, indent=2)

    def log_metrics(metrics: dict[str, float], step: int):
        """Logs to TensorBoard and, with --track, to MLflow (in one request)."""
        for key, value in metrics.items():
            writer.add_scalar(key, value, step)
        if args.track:
            mlflow.log_metrics(metrics, step=step)

    # TRY NOT TO MODIFY: seeding
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.backends.cudnn.deterministic = args.torch_deterministic

    device = torch.device("cuda" if torch.cuda.is_available() and args.cuda else "cpu")

    # env setup
    # all the games run in lockstep, with the opponents' moves batched on the training device
    envs = JassVecEnv(args.num_envs, device, args.supervised_model, hand_episodes=args.hand_episodes,
                      opponent_model_paths=args.opponent_models, pool_size=args.pool_size,
                      fixed_opponent_fraction=args.fixed_opponent_fraction)
    assert isinstance(envs.single_action_space, gym.spaces.Discrete), "only discrete action space is supported"
    print(f"Fixed opponents: {', '.join(envs.fixed_opponent_groups)}"
          + (f" ({args.fixed_opponent_fraction:.0%} of the games once the pool of {args.pool_size} snapshots has one)"
             if args.pool_size > 0 else ""))

    agent = JassFormerActorCritic(d_model=256)
    if args.init_checkpoint:
        agent.load_state_dict(torch.load(args.init_checkpoint, map_location="cpu"))
        print(f"Continuing from {args.init_checkpoint} (policy and value head)")
    else:
        JassFormerActorCritic.load_policy_weights(agent, args.supervised_model)
    agent = agent.to(device)

    # the value head starts from random weights: it gets its own, higher learning rate (param group 1)
    policy_params = [p for name, p in agent.named_parameters() if not name.startswith("value_head.")]
    optimizer = optim.Adam([{"params": policy_params, "lr": args.learning_rate},
                            {"params": agent.value_head.parameters(), "lr": args.warmup_value_learning_rate}], eps=1e-5)

    # ALGO Logic: Storage setup
    obs = torch.zeros((args.num_steps, args.num_envs) + envs.single_observation_space.shape,dtype=torch.int64).to(device)
    actions = torch.zeros((args.num_steps, args.num_envs) + envs.single_action_space.shape).to(device)
    logprobs = torch.zeros((args.num_steps, args.num_envs)).to(device)
    rewards = torch.zeros((args.num_steps, args.num_envs)).to(device)
    dones = torch.zeros((args.num_steps, args.num_envs)).to(device)
    values = torch.zeros((args.num_steps, args.num_envs)).to(device)

    # TRY NOT TO MODIFY: start the game
    global_step = 0
    start_time = time.time()
    next_obs, _ = envs.reset(seed=args.seed)
    next_obs = torch.from_numpy(next_obs).to(device)
    next_done = torch.zeros(args.num_envs).to(device)

    for iteration in range(1, args.num_iterations + 1):
        envs.next_pool_opponent()
        # During warmup, only the value head is trained. Freezing the shared backbone is required as well,
        # otherwise the value loss would still change the policy through it.
        in_warmup = global_step < args.warmup_steps
        for name, param in agent.named_parameters():
            param.requires_grad = not in_warmup or name.startswith("value_head.")

        # Annealing the rate if instructed to do so.
        frac = 1.0 - (iteration - 1.0) / args.num_iterations if args.anneal_lr else 1.0
        optimizer.param_groups[0]["lr"] = frac * args.learning_rate
        optimizer.param_groups[1]["lr"] = frac * (args.warmup_value_learning_rate if in_warmup
                                                  else args.value_learning_rate)

        episode_returns, episode_lengths, episode_wins, episode_opponents = [], [], [], []
        for step in range(0, args.num_steps):
            global_step += args.num_envs
            obs[step] = next_obs
            dones[step] = next_done

            # ALGO LOGIC: action logic
            with torch.no_grad():
                action, logprob, _, value = agent.get_action_and_value(next_obs)
                values[step] = value.flatten()
            actions[step] = action
            logprobs[step] = logprob

            # TRY NOT TO MODIFY: execute the game and log data.
            next_obs, reward, terminations, truncations, infos = envs.step(action.cpu().numpy())
            next_done = np.logical_or(terminations, truncations)
            rewards[step] = torch.tensor(reward).to(device).view(-1)
            next_obs, next_done = torch.from_numpy(next_obs).to(device), torch.Tensor(next_done).to(device)

            # episode statistics are batched over the envs, "_episode" flags the envs that just finished
            final_info = infos.get("final_info", {})
            if "episode" in final_info:
                finished = final_info["_episode"]
                episode_returns += final_info["episode"]["r"][finished].tolist()
                episode_lengths += final_info["episode"]["l"][finished].tolist()
                episode_wins += final_info["episode"]["won"][finished].tolist()
                episode_opponents += final_info["episode"]["opponent"][finished].tolist()

        # bootstrap value if not done
        with torch.no_grad():
            next_value = agent.get_value(next_obs).reshape(1, -1)
            advantages = torch.zeros_like(rewards).to(device)
            lastgaelam = 0
            for t in reversed(range(args.num_steps)):
                if t == args.num_steps - 1:
                    nextnonterminal = 1.0 - next_done
                    nextvalues = next_value
                else:
                    nextnonterminal = 1.0 - dones[t + 1]
                    nextvalues = values[t + 1]
                delta = rewards[t] + args.gamma * nextvalues * nextnonterminal - values[t]
                advantages[t] = lastgaelam = delta + args.gamma * args.gae_lambda * nextnonterminal * lastgaelam
            returns = advantages + values

        # flatten the batch
        b_obs = obs.reshape((-1,) + envs.single_observation_space.shape)
        b_logprobs = logprobs.reshape(-1)
        b_actions = actions.reshape((-1,) + envs.single_action_space.shape)
        b_advantages = advantages.reshape(-1)
        b_returns = returns.reshape(-1)
        b_values = values.reshape(-1)

        # Optimizing the policy and value network
        b_inds = np.arange(args.batch_size)
        clipfracs = []
        minibatch_updates = 0
        kl_exceeded = False
        for epoch in range(args.update_epochs):
            np.random.shuffle(b_inds)
            for start in range(0, args.batch_size, args.minibatch_size):
                end = start + args.minibatch_size
                mb_inds = b_inds[start:end]

                _, newlogprob, entropy, newvalue = agent.get_action_and_value(b_obs[mb_inds], b_actions.long()[mb_inds])
                logratio = newlogprob - b_logprobs[mb_inds]
                ratio = logratio.exp()

                with torch.no_grad():
                    # calculate approx_kl http://joschu.net/blog/kl-approx.html
                    old_approx_kl = (-logratio).mean()
                    approx_kl = ((ratio - 1) - logratio).mean()
                    clipfracs += [((ratio - 1.0).abs() > args.clip_coef).float().mean().item()]
                # stops before a minibatch that would move the policy further: checking at the end of each epoch only
                # lets a whole epoch overshoot
                if args.target_kl is not None and approx_kl > args.target_kl:
                    kl_exceeded = True
                    break

                mb_advantages = b_advantages[mb_inds]
                if args.norm_adv:
                    mb_advantages = (mb_advantages - mb_advantages.mean()) / (mb_advantages.std() + 1e-8)

                # Policy loss
                pg_loss1 = -mb_advantages * ratio
                pg_loss2 = -mb_advantages * torch.clamp(ratio, 1 - args.clip_coef, 1 + args.clip_coef)
                pg_loss = torch.max(pg_loss1, pg_loss2).mean()

                # Value loss
                newvalue = newvalue.view(-1)
                if args.clip_vloss:
                    v_loss_unclipped = (newvalue - b_returns[mb_inds]) ** 2
                    v_clipped = b_values[mb_inds] + torch.clamp(
                        newvalue - b_values[mb_inds],
                        -args.clip_coef,
                        args.clip_coef,
                    )
                    v_loss_clipped = (v_clipped - b_returns[mb_inds]) ** 2
                    v_loss_max = torch.max(v_loss_unclipped, v_loss_clipped)
                    v_loss = 0.5 * v_loss_max.mean()
                else:
                    v_loss = 0.5 * ((newvalue - b_returns[mb_inds]) ** 2).mean()

                entropy_loss = entropy.mean()

                if in_warmup:
                    # WARMUP PHASE: Only optimize Value Loss (all other parameters are frozen above)
                    loss = v_loss * args.vf_coef
                else:
                    # STANDARD PPO: Train everything
                    loss = pg_loss - args.ent_coef * entropy_loss + v_loss * args.vf_coef

                optimizer.zero_grad()
                loss.backward()
                nn.utils.clip_grad_norm_(agent.parameters(), args.max_grad_norm)
                optimizer.step()
                minibatch_updates += 1

            if kl_exceeded:
                break

        y_pred, y_true = b_values.cpu().numpy(), b_returns.cpu().numpy()
        var_y = np.var(y_true)
        explained_var = np.nan if var_y == 0 else 1 - np.var(y_true - y_pred) / var_y

        sps = int(global_step / (time.time() - start_time))
        metrics = {
            "charts/learning_rate": optimizer.param_groups[0]["lr"],
            "charts/value_learning_rate": optimizer.param_groups[1]["lr"],
            "charts/SPS": sps,
            "charts/warmup": float(in_warmup),
            "losses/value_loss": v_loss.item(),
            "losses/policy_loss": pg_loss.item(),
            "losses/entropy": entropy_loss.item(),
            "losses/old_approx_kl": old_approx_kl.item(),
            "losses/approx_kl": approx_kl.item(),
            "losses/clipfrac": float(np.mean(clipfracs)),
            "losses/minibatch_updates": minibatch_updates,
            "losses/explained_variance": float(explained_var),
        }
        if episode_returns:
            # averaged over the episodes (hands or games) finished during this iteration, against all the opponents,
            # then by opponent: each fixed opponent, and the pool
            metrics |= {
                "charts/episodic_return": float(np.mean(episode_returns)),
                "charts/episodic_length": float(np.mean(episode_lengths)),
                "charts/episode_win_rate": float(np.mean(episode_wins)),
                "charts/episodes": len(episode_returns),
            }
            if args.hand_episodes:
                metrics["charts/points_per_hand"] = float(np.mean(episode_returns)) * 157
            unit_name = "hand" if args.hand_episodes else "game"
            for opponent in sorted(set(episode_opponents)):
                selected = [i for i, o in enumerate(episode_opponents) if o == opponent]
                returns = np.array(episode_returns)[selected]
                key = re.sub(r"[^A-Za-z0-9_.-]", "_", opponent)
                metrics[f"opponents/{unit_name}s_vs_{key}"] = len(selected)
                metrics[f"opponents/win_rate_vs_{key}"] = float(np.mean(np.array(episode_wins)[selected]))
                if args.hand_episodes:
                    metrics[f"opponents/points_per_hand_vs_{key}"] = float(np.mean(returns)) * 157
                else:
                    metrics[f"opponents/return_vs_{key}"] = float(np.mean(returns))
        metrics["opponents/pool_size"] = envs.pool_length
        log_metrics(metrics, global_step)
        unit = "hands" if args.hand_episodes else "games"
        if not episode_returns:
            games_summary = f"no {unit[:-1]} finished"
        elif args.hand_episodes:
            games_summary = (f"{np.mean(episode_returns) * 157:+.1f} points/hand, won {np.mean(episode_wins):.0%} "
                             f"of {len(episode_returns)} hands")
            by_opponent = {k[len("opponents/points_per_hand_vs_"):]: v for k, v in metrics.items()
                           if k.startswith("opponents/points_per_hand_vs_")}
            if len(by_opponent) > 1:
                games_summary += " (" + ", ".join(f"{o} {v:+.1f}" for o, v in by_opponent.items()) + ")"
        else:
            games_summary = (f"return {np.mean(episode_returns):+.2f}, won {np.mean(episode_wins):.0%} of "
                             f"{len(episode_returns)} games")
        print(f"iter {iteration}/{args.num_iterations} | step {global_step:,} | {sps} SPS"
              f"{' | warmup' if in_warmup else ''} | {games_summary} | value loss {v_loss.item():.3f}, "
              f"explained var {explained_var:.2f}, approx kl {approx_kl.item():.4f}, clipfrac {np.mean(clipfracs):.3f}, "
              f"{minibatch_updates}/{args.update_epochs * args.num_minibatches} minibatches")

        # --- SELF-PLAY LEAGUE UPDATE ---
        if iteration % args.opponent_update_freq == 0:
            save_path = os.path.join(run_dir, f"model_iter_{iteration}.pth")
            torch.save(agent.state_dict(), save_path)
            print(f"Saved {save_path}" + (", added to the pool of opponents" if args.pool_size > 0 else ""))
            if args.track and args.mlflow_log_checkpoints:
                mlflow.log_artifact(save_path, artifact_path="checkpoints")
            envs.add_opponent_snapshot(agent.state_dict())

    save_path = os.path.join(run_dir, "model_final.pth")
    torch.save(agent.state_dict(), save_path)
    onnx_path = os.path.join(run_dir, "model_final.onnx")
    export_rl_model_to_onnx(save_path, onnx_path, agent.embedding.embedding_dim)
    print(f"Saved {save_path} and {onnx_path}")
    if args.track:
        mlflow.log_artifact(save_path, artifact_path="checkpoints")
        mlflow.log_artifact(onnx_path)
        mlflow.end_run()

    envs.close()
    writer.close()
