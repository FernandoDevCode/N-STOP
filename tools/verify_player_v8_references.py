#!/usr/bin/env python3
"""Verify canonical Player V8 visual master PNGs against the committed manifest."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "docs/player-v8/references"
manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
for entry in manifest["items"]:
    path = root / entry["destination"]
    if not path.is_file():
        raise SystemExit(f"Missing V8 visual reference: {path.name}")
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual.lower() != entry["sha256"].lower():
        raise SystemExit(f"Invalid SHA256 for {path.name}: {actual}")
    print(f"Verified {path.name}: {actual}")
