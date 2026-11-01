#!/usr/bin/env python3
"""Check three candidate CDN image variants, preserving their hashes."""
import io
import requests
from PIL import Image
from pathlib import Path
from bs4 import BeautifulSoup

hashes = ("ddc39f9fa7c94cb05c6268e8d2671cc2",
          "11759a781b9990459403914d63b70d60",
          "2a6f85be690528a2cdbe97d567709b53")
lines = ["", "## Photo resolution variant verification", "",
         "Tested a small, fixed sample of three Slipknot gallery image hashes.", "",
         "| Hash | Variant | HTTP | Dimensions | Bytes |", "|---|---|---|---|---|"]
for h in hashes:
    for variant in ("avatar170s", "ar0", "770x0"):
        u = "https://lastfm-img.freetls.fastly.net/i/u/" + variant + "/" + h
        try:
            r = requests.get(u, headers={"User-Agent":"Mozilla/5.0"},timeout=15)
            dim = "unavailable"
            if r.status_code == 200 and r.headers.get("content-type","").startswith("image/"):
                img=Image.open(io.BytesIO(r.content))
                dim = str(img.width)+"x"+str(img.height)
            lines += ["| " + h[:10] + " | " + variant + " | " + str(r.status_code) +
                      " | " + dim + " | " + str(len(r.content)) + " |"]
        except Exception as e:
            lines += ["| " + h[:10] + " | " + variant + " | ERROR | " + type(e).__name__ + " | 0 |"]
path=Path("lastfm-report.md")
with path.open("a",encoding="utf-8") as f: f.write("\n".join(lines)+"\n")
print("\n".join(lines))
