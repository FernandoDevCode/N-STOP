#!/usr/bin/env python3
"""Regenerate Sonora launcher assets from the approved exact icon image.

Source: app/src/main/res/drawable-nodpi/sonora_launcher_final.webp
Run: python -m pip install Pillow && python tools/regenerate_sonora_launcher.py
Commit output PNGs. Android builds need no Python dependencies.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps, ImageChops

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SRC = RES / "drawable-nodpi/sonora_launcher_final.webp"

with Image.open(SRC) as opened:
    artwork = opened.convert("RGBA")
assert artwork.size == (512, 512), f"Expected 512x512 launcher, got {artwork.size}"

def save_rgba(image: Image.Image, dest: Path) -> None:
    image.save(dest, format="PNG", optimize=True)
    with Image.open(dest) as check:
        check.load()
        assert check.mode == "RGBA" and check.size == image.size
    assert dest.read_bytes()[25] == 6

# Preserve the complete approved composition, including the subtle dark curves.
save_rgba(artwork.resize((288, 288), Image.Resampling.LANCZOS),
          RES / "drawable-nodpi/sonora_icon_foreground.png")

# Android themed-icon monochrome is a bright-emblem silhouette, not the black tile.
brightness = ImageOps.grayscale(artwork)
white_alpha = brightness.point(lambda v: int(max(0, min(255, (v-69)*255/120))))
white_alpha = ImageChops.multiply(white_alpha, artwork.getchannel("A"))
mono = Image.new("RGBA", artwork.size, (255, 255, 255, 0))
mono.putalpha(white_alpha)
save_rgba(mono.resize((288,288), Image.Resampling.LANCZOS),
          RES / "drawable-nodpi/sonora_icon_monochrome.png")

for density, size in (("mdpi",48), ("hdpi",72), ("xhdpi",96),
                      ("xxhdpi",144), ("xxxhdpi",192)):
    save_rgba(artwork.resize((size,size), Image.Resampling.LANCZOS),
              RES / f"mipmap-{density}/ic_launcher.png")
    # Some OEMs load round legacy artwork separately from the adaptive icon.
    round_mask = Image.new("L", artwork.size, 0)
    ImageDraw.Draw(round_mask).ellipse((0,0,511,511), fill=255)
    round_art = artwork.copy()
    round_art.putalpha(ImageChops.multiply(artwork.getchannel("A"), round_mask))
    save_rgba(round_art.resize((size,size), Image.Resampling.LANCZOS),
              RES / f"mipmap-{density}/ic_launcher_round.png")

print("Regenerated 12 RGBA Sonora launcher fallback PNGs from the approved artwork.")
