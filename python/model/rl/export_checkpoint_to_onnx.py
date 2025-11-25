import argparse

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.export import Dim

from dataset import VOCABULARY_SIZE, TOKEN_LENGTH
from model import JassFormerActorCritic


class JassInferenceWrapper(nn.Module):
    def __init__(self, original_agent):
        super().__init__()
        self.agent = original_agent

    def forward(self, x):
        # 1. Run the Transformer Backbone
        # x shape: (batch_size, sequence_length)
        cls_token = self.agent.forward_backbone(x)

        # 2. Get Logits from both heads
        card_logits = self.agent.policy_head(cls_token)  # 36 cards
        trump_logits = self.agent.trump_head(cls_token)  # 6 trumps

        return F.log_softmax(card_logits, dim=-1), F.log_softmax(trump_logits, dim=-1)

def export_to_onnx(checkpoint_path, output_path="jass_agent.onnx", d_model=256):
    print(f"Loading checkpoint from: {checkpoint_path}")

    # 1. Initialize the Full Model (Architecture must match training)
    # Ensure arguments (d_model, etc.) match your training config
    full_agent = JassFormerActorCritic(d_model=d_model)

    # 2. Load Weights
    # use map_location='cpu' to ensure it works even without GPU
    state_dict = torch.load(checkpoint_path, map_location="cpu")
    full_agent.load_state_dict(state_dict)
    full_agent.eval() # Crucial: turns off Dropout

    # 3. Wrap it
    inference_model = JassInferenceWrapper(full_agent)

    # 4. Create Dummy Input
    # Batch size 1, 96 tokens (integers)
    dummy_input = torch.randint(0, VOCABULARY_SIZE, (1, TOKEN_LENGTH), dtype=torch.long)

    # 5. Export
    print(f"Exporting to {output_path}...")
    torch.onnx.export(
        inference_model,               # The wrapper model
        (dummy_input,),                   # Dummy input
        output_path,                   # Output filename
        export_params=True,            # Store the trained weights inside the file
        opset_version=18,              # 11 or 17 are usually safest for Java
        do_constant_folding=True,      # Optimization
        input_names=['tokens'],  # Name the input node for Java lookup
        output_names=['logits'],# Name the output node for Java lookup
        dynamic_shapes=({0: Dim("batch", min=1)},),
        external_data=False,
    )
    print("✅ Export success!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export JassFormer PyTorch model to ONNX")

    parser.add_argument(
        "--checkpoint",
        type=str,
        required=True,
        help=f"Path to the .pth checkpoint file"
    )

    parser.add_argument(
        "--output",
        type=str,
        default="jass_agent.onnx",
        help="Path where the .onnx file will be saved (default: jass_agent.onnx)"
    )

    parser.add_argument(
        "--d_model",
        type=int,
        default=256,
        help="Dimension of the model (d_model) used during training (default: 256)"
    )

    args = parser.parse_args()

    export_to_onnx(args.checkpoint, args.output, args.d_model)
