#!/usr/bin/env python3
"""Read only: one public HTML page per artist, no login, no retries."""
import re
from urllib.parse import quote_plus, urljoin
import requests
from bs4 import BeautifulSoup
from pathlib import Path

artists = ["Slipknot", "Radiohead", "System of a Down"]
lines = ["# Last.fm public artist-gallery test", "", "No login, cookies or image downloads.", ""]
for name in artists:
    url = "https://www.last.fm/music/" + quote_plus(name) + "/+images"
    lines += ["## " + name, "", "Gallery: " + url]
    try:
        response = requests.get(url, timeout=20, headers={"User-Agent":"Mozilla/5.0"})
        lines += ["Status: " + str(response.status_code), "HTML bytes: " + str(len(response.content)),
                  "Final URL: " + response.url]
        if response.status_code != 200:
            lines += ["Stopped (no bypass).", ""]
            continue
        soup = BeautifulSoup(response.text, "html.parser")
        links, seen = [], set()
        for anchor in soup.find_all("a", href=True):
            href = anchor["href"]
            if not re.search(r"/\+images/[0-9a-f]{20,}", href, re.I): continue
            target = urljoin(response.url, href.split("?")[0])
            if target in seen: continue
            seen.add(target)
            img = anchor.find("img")
            links.append((target,img.get("src","") if img else ""))
        lines += ["Unique photo links: " + str(len(links))]
        for index,(href,thumbnail) in enumerate(links[:15],1):
            lines.append(str(index) + ". " + href)
            if thumbnail: lines.append("  preview " + thumbnail[:220])
        sorts = [x.get_text(" ",strip=True) for x in soup.select("a,option,button")
                 if "popular" in x.get_text(" ",strip=True).lower()]
        lines += ["Popularity controls: " + repr(sorts[:10]), ""]
    except requests.RequestException as exc:
        lines += ["Error: " + str(exc), ""]
Path("lastfm-report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("\n".join(lines))
