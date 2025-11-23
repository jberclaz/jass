# train_sf.py  ← Run with: python train_sf.py --env=JassSelfPlay [other flags]
from sample_factory.algo.runners.runner import Runner
from sample_factory.cfg.arguments import parse_sf_args, parse_full_cfg
from sample_factory.envs.env_utils import register_env
from sample_factory.algo.utils.context import global_model_factory

from jass_env import JassEnv
from model import JassFormerActorCritic


# Register your env and model (runs on import)
def make_env(full_env_name, cfg=None, env_config=None, render_mode=None):
    return JassEnv()


register_env("JassSelfPlay", make_env)
global_model_factory().register_actor_critic_factory(
    lambda: JassFormerActorCritic(d_model=256)
)


def main():
    # Parse from actual command line args
    parser, cfg = parse_sf_args()
    cfg = parse_full_cfg(parser)

    # Launch the runner
    runner = Runner(cfg)
    status = runner.run()
    return status


if __name__ == "__main__":
    import sys

    sys.exit(main())