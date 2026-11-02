#!/usr/bin/env python3
"""Regenerate Sonora launchers from the approved artwork, never from the Android template.

Run locally with: python -m pip install Pillow && python tools/regenerate_sonora_launcher.py
The resulting PNGs are committed; normal Android builds have no Python dependency.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SRC = RES / "drawable-nodpi/sonora_icon_foreground.png"

# Validate the exact approved source before decoding. The v183 build contained a
# corrupted PNG with an intact header; checking image dimensions was insufficient.
import hashlib
source_bytes = SRC.read_bytes()
source_sha = hashlib.sha256(source_bytes).hexdigest()
with Image.open(SRC) as check_source:
    if check_source.mode == "P":
        assert source_sha == "e1c2d05e937bdd4bf34d3994a448ae69703325868d70a9406dbe90a77f8063b1", f"Unexpected source hash: {source_sha}"
original = Image.open(SRC).convert("RGBA")
alpha = original.getchannel("A")
bounds = alpha.getbbox()
assert bounds is not None, "Approved symbol is missing"
symbol = original.crop(bounds)

# Foreground is true 32-bit RGBA, not indexed + tRNS. All pixels come from
# the same approved artwork; resize only adjusts optical size for launcher.
S = 288
target_height = 174
target_width = round(target_height * symbol.width / symbol.height)
scaled = symbol.resize((target_width, target_height), Image.Resampling.LANCZOS)
foreground = Image.new("RGBA", (S, S), (0, 0, 0, 0))
foreground.alpha_composite(scaled, ((S - target_width)//2, (S - target_height)//2))
foreground.save(SRC, format="PNG", optimize=True)

monochrome = Image.new("RGBA", (S, S), (255, 255, 255, 0))
monochrome.putalpha(foreground.getchannel("A"))
monochrome.save(RES / "drawable-nodpi/sonora_icon_monochrome.png", format="PNG", optimize=True)

# Legacy mipmaps are essential fallback paths for OEM launchers. Replace all
# old Android-template icons rather than assuming adaptive icons always load.
BASE = 384
def legacy_icon(round_mask: bool):
    canvas = Image.new("RGBA", (BASE, BASE), (35, 32, 46, 255))
    m = Image.new("L", (BASE, BASE), 0)
    draw = ImageDraw.Draw(m)
    if round_mask:
        draw.ellipse((0, 0, BASE-1, BASE-1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, BASE-1, BASE-1), radius=82, fill=255)
    canvas.putalpha(m)
    img = foreground.resize((BASE, BASE), Image.Resampling.LANCZOS)
    canvas.alpha_composite(img)
    return canvas

icons = {"ic_launcher.png": legacy_icon(False),
         "ic_launcher_round.png": legacy_icon(True)}
for name, image in icons.items():
    for density, pixels in (("mdpi",48),("hdpi",72),("xhdpi",96),
                            ("xxhdpi",144),("xxxhdpi",192)):
        dest = RES / ("mipmap-" + density) / name
        image.resize((pixels,pixels), Image.Resampling.LANCZOS).save(dest, format="PNG", optimize=True)

# Fail immediately if a generated resource is indexed or malformed.
files = [SRC, RES/"drawable-nodpi/sonora_icon_monochrome.png"]
files += [RES / ("mipmap-" + density) / name for density in
          ("mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi") for name in icons]
for dest in files:
    with Image.open(dest) as check:
        check.load()
        assert check.mode == "RGBA", f"Not RGBA: {dest}"
    assert dest.read_bytes()[25] == 6, f"Not PNG truecolor-alpha: {dest}"
print(f"Verified {len(files)} Sonora PNG resources (RGBA, PNG color type 6).")
