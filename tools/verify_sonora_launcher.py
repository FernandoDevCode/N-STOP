#!/usr/bin/env python3
"""Reject unreadable/unsupported Android launcher PNGs before publishing a Sonora APK."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FILES = {
    RES / "drawable-nodpi/sonora_icon_foreground.png": (288, 288),
    RES / "drawable-nodpi/sonora_icon_monochrome.png": (288, 288),
}
for density, px in DENSITIES.items():
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        FILES[RES / f"mipmap-{density}" / name] = (px, px)


def check(path: Path, dimensions: tuple[int, int]) -> None:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: bad PNG signature"
    off = 8
    compressed = bytearray()
    size = None
    done = False
    while off < len(data):
        assert off + 12 <= len(data), f"{path}: truncated chunk"
        length = struct.unpack_from(">I", data, off)[0]
        chunk_type = data[off + 4:off + 8]
        payload = data[off + 8:off + 8 + length]
        crc_end = off + 8 + length + 4
        assert crc_end <= len(data), f"{path}: truncated payload"
        crc = struct.unpack_from(">I", data, off + 8 + length)[0]
        assert zlib.crc32(chunk_type + payload) & 0xFFFFFFFF == crc, f"{path}: CRC mismatch"
        if chunk_type == b"IHDR":
            w, h, bits, color, *_ = struct.unpack(">IIBBBBB", payload)
            size = (w, h)
            assert (bits, color) == (8, 6), f"{path}: expected truecolor RGBA"
        elif chunk_type == b"IDAT":
            compressed.extend(payload)
        elif chunk_type == b"IEND":
            done = True
            assert crc_end == len(data), f"{path}: unexpected bytes after PNG"
        off = crc_end
    assert done and size == dimensions and compressed, f"{path}: invalid dimensions/chunks"
    raw = zlib.decompress(bytes(compressed))
    row_size = size[0] * 4 + 1
    assert len(raw) == row_size * size[1], f"{path}: incomplete raster"
    assert all(raw[y * row_size] <= 4 for y in range(size[1])), f"{path}: invalid PNG filter"


for file, expected in FILES.items():
    check(file, expected)
print(f"Verified {len(FILES)} complete RGBA Sonora launcher PNGs.")
