import sys
import threading

import torch
from transformers import (AutoModelForCausalLM, AutoTokenizer,
                          TextIteratorStreamer, BitsAndBytesConfig)

# Path to the model directory
model_path = "/home/jim/src/DeepSeek-R1-Distill-Qwen-1.5B"

# Check if 4-bit mode is enabled (pass "--4bit" as a command-line argument)
use_4bit = "--4bit" in sys.argv

# Load tokenizer
tokenizer = AutoTokenizer.from_pretrained(model_path, trust_remote_code=True)

# Configure quantization if 4-bit mode is enabled
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_compute_dtype=torch.float16,
    bnb_4bit_quant_type="nf4",
    llm_int8_enable_fp32_cpu_offload=True
) if use_4bit else None

# Load model with appropriate configuration
model = AutoModelForCausalLM.from_pretrained(
    model_path,
    quantization_config=bnb_config if use_4bit else None,
    torch_dtype=None if use_4bit else torch.float16,
    device_map="auto",
    trust_remote_code=True
)

def generate_text(prompt):
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    streamer = TextIteratorStreamer(tokenizer, skip_special_tokens=True)

    # Generate in a background thread to avoid blocking
    thread = threading.Thread(target=model.generate, kwargs=dict(
        **inputs,
        max_new_tokens=tokenizer.model_max_length,
        temperature=0.7,
        do_sample=True,
        top_k=50,
        top_p=0.9,
        repetition_penalty=1.2,
        eos_token_id=tokenizer.eos_token_id,
        streamer=streamer
    ))
    thread.start()

    # Collect output in a string
    generated_text = ""
    for text in streamer:
        print(text, end="", flush=True)
    
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python run_deepseek.py \"Your prompt here\" [--4bit]")
        sys.exit(1)
    prompt = sys.argv[1]
    print('-----------------------------------------------------')
    print(prompt)
    print('-----------------------------------------------------')
    generate_text(prompt)
    print()
