#!/usr/bin/env python3
"""Regenerate Sonora launcher assets from the approved square artwork.

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
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, format="PNG", optimize=True)
    with Image.open(dest) as check:
        check.load()
        assert check.mode == "RGBA" and check.size == image.size
    assert dest.read_bytes()[25] == 6


# The adaptive viewport is 108 dp, with a central 72 dp nominal visible region.
# Scale the approved artwork to 2/3 of the layer, centered, so the visible mark
# has the same proportions as the legacy icon. The Android launcher owns the
# outer mask; no baked rounded square or dark tile in either foreground.
brightness = ImageOps.grayscale(artwork)
alpha = brightness.point(lambda v: max(0, min(255, round((v - 16) * 255 / 222))))
alpha = ImageChops.multiply(alpha, artwork.getchannel("A"))
# Solid mineral white, preserving the approved image's antialiased silhouette.
mark = Image.new("RGBA", artwork.size, (241, 239, 235, 0))
mark.putalpha(alpha)
mono = Image.new("RGBA", artwork.size, (255, 255, 255, 0))
mono.putalpha(alpha)
for image, name in ((mark, "sonora_icon_foreground.png"),
                    (mono, "sonora_icon_monochrome.png")):
    layer = Image.new("RGBA", (288, 288))
    layer.paste(image.resize((192, 192), Image.Resampling.LANCZOS), (48, 48))
    save_rgba(layer, RES / "drawable-nodpi" / name)

for density, size in (("mdpi",48), ("hdpi",72), ("xhdpi",96),
                      ("xxhdpi",144), ("xxxhdpi",192)):
    save_rgba(artwork.resize((size,size), Image.Resampling.LANCZOS),
              RES / f"mipmap-{density}/ic_launcher.png")
    round_mask = Image.new("L", artwork.size, 0)
    ImageDraw.Draw(round_mask).ellipse((0,0,511,511), fill=255)
    round_art = artwork.copy()
    round_art.putalpha(ImageChops.multiply(artwork.getchannel("A"), round_mask))
    save_rgba(round_art.resize((size,size), Image.Resampling.LANCZOS),
              RES / f"mipmap-{density}/ic_launcher_round.png")

print("Regenerated 12 RGBA Sonora launcher PNGs from the approved artwork.")
