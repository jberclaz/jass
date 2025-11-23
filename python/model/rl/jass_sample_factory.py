# jass_sample_factory.py
import torch
from sample_factory.algo.runners.runner import Runner
from sample_factory.cfg.arguments import parse_full_cfg, parse_sf_args
from sample_factory.envs.env_utils import register_env
from sample_factory.algo.utils.context import global_model_factory
from sample_factory.algo.learning.learner import Learner
from model import JassFormerActorCritic
from jass_env import JassEnv


def make_jass_env(full_env_name, cfg, env_config, render_mode=None, **kwargs):
    return JassEnv()


def override_defaults(parser):
    parser.set_defaults(
        train_dir="./sf_jass_logs",
        experiment="jass_selfplay",
        num_workers=16,
        num_envs_per_worker=8,
        batch_size=4096,
        ppo_epochs=3,
        rollout=32,
        max_grad_norm=0.5,
        learning_rate=3e-4,
        gae_lambda=0.95,
        gamma=0.99,
        device="gpu",
    )


def add_extra_params(parser):
    p = parser.add_argument_group("Jass Specific")
    p.add_argument("--self_play", type=lambda x: x.lower() == "true", default=True)


def register_jass_components():
    register_env("JassSelfPlay", make_jass_env)
    global_model_factory().register_actor_critic_factory(
        lambda: JassFormerActorCritic(d_model=256)
    )


def main():
    register_jass_components()

    parser, cfg = parse_sf_args(argv=[], evaluation=False)
    override_defaults(parser)
    add_extra_params(parser)
    cfg = parse_full_cfg(parser)

    status = Runner(cfg).run()
    return status


if __name__ == "__main__":
    import sys
    sys.exit(main())