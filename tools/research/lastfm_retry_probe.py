#!/usr/bin/env python3
"""Bounded, unauthenticated Last.fm gallery retry experiment, not a production scraper."""
import re
import time
from pathlib import Path
from urllib.parse import quote_plus, urljoin

import requests
from bs4 import BeautifulSoup

ARTISTS = ("Slipknot", "Radiohead", "System of a Down")
MAX_ATTEMPTS = 4  # first try + up to three retries
PAUSES = (2, 4, 8)  # bounded exponential backoff
USER_AGENT = "SonoraLastFmFeasibility/0.1 (non-commercial read-only test)"
OUTPUT = Path("docs/research/lastfm-retry-result.md")
rows = [
    "# Last.fm gallery: retry/session experiment",
    "",
    "Run: GitHub Actions. 3 artists, at most 4 GETs per artist, 2/4/8 second pauses.",
    "Attempts 1–3 reuse one requests.Session; attempt 4 uses a fresh Session.",
    "No login, cookies supplied, cache library, CAPTCHA solving or proxy rotation.",
    "Python requests.Session does **not** have a persistent cache to clear.",
    "HTTP 200 is counted as success **only** when real gallery image links are present.",
    "HTTP 403 or 429 and identifiable challenge pages stop retries.",
    "",
    "| Artist | Attempt | Session | HTTP | HTML bytes | Photo links | Popularity label | Cache evidence | Result |",
    "|---|---:|---|---:|---:|---:|---|---|---|",
]

for artist in ARTISTS:
    url = "https://www.last.fm/music/" + quote_plus(artist) + "/+images"
    session = requests.Session()
    success = False
    for attempt in range(1, MAX_ATTEMPTS + 1):
        if attempt == 4:
            session.close()
            session = requests.Session()
        mode = "fresh" if attempt == 4 else "reused"
        try:
            response = session.get(
                url, timeout=(8, 18), allow_redirects=True,
                headers={"User-Agent": USER_AGENT},
            )
            html = response.text
            soup = BeautifulSoup(html, "html.parser")
            unique = list(dict.fromkeys(
                urljoin(response.url, a["href"].split("?")[0])
                for a in soup.find_all("a", href=True)
                if re.search(r"/\+images/[a-f0-9]{20,}", a["href"], re.I)
            ))
            popular = "most popular" in soup.get_text(" ", strip=True).lower()
            cache_header = response.headers.get("X-Cache") or response.headers.get("CF-Cache-Status") or "-"
            # Only explicit blocking/challenge evidence stops retries.
            title = soup.title.get_text(" ", strip=True)[:90] if soup.title else "(no title)"
            lower = (title + " " + html[:6000]).lower()
            challenged = any(p in lower for p in (
                "captcha", "access denied", "checking your browser",
                "just a moment...", "verify you are human",
            ))
            if response.status_code in (403, 429):
                outcome = "STOP: access or rate limited"
            elif challenged:
                outcome = "STOP: challenge page"
            elif unique and popular:
                outcome = "OK: ranked gallery"
            elif unique:
                outcome = "PARTIAL: photos, ranking unverified"
            else:
                outcome = "INVALID: no gallery in HTML"
            rows.append(
                f"| {artist} | {attempt} | {mode} | {response.status_code} | "
                f"{len(response.content)} | {len(unique)} | "
                f"{'yes' if popular else 'no'} | {cache_header[:35]} | {outcome} |"
            )
            if attempt == 1 or (outcome.startswith("OK:")):
                rows.append(f"<!-- {artist} attempt {attempt} title: {title} -->")
            if unique:
                rows.append(f"<!-- {artist} first photo: {unique[0]} -->")
            print(f"{artist}: attempt={attempt}, status={response.status_code}, "
                  f"bytes={len(response.content)}, photos={len(unique)}, "
                  f"ranked={popular}, outcome={outcome}", flush=True)
            if unique and popular:
                success = True
                break
            if response.status_code in (403,429) or challenged:
                break
        except requests.RequestException as exc:
            rows.append(f"| {artist} | {attempt} | {mode} | ERROR | - | - | - | - | {type(exc).__name__} |")
            print(f"{artist} attempt={attempt}: {type(exc).__name__}", flush=True)
        if attempt < MAX_ATTEMPTS:
            time.sleep(PAUSES[attempt-1])
    session.close()
    rows.extend(["", f"{artist}: {'ranked gallery obtained' if success else 'ranked gallery not obtained'}.", ""])

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text("\n".join(rows)+"\n", encoding="utf-8")
print("REPORT: "+str(OUTPUT), flush=True)
