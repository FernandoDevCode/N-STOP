#!/usr/bin/env python3
"""CI gate: prevent user-visible legacy branding and excluded Sonora music sources.

This deliberately does NOT rename com.stash namespaces, database fields, old
encrypted preferences, legacy App Links or mandatory GPL attribution.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LEGACY = re.compile(
    r"\bStash\b|NØNSTOP|NØSTOP|\bQobuz\b|\bLast\.fm\b|\bListenBrainz\b|"
    r"\bApple Music\b|rawna?ldclark|ko-fi\.com/rawnald|play\.stashfm\.app",
    re.IGNORECASE,
)
DISPLAY_CALL = re.compile(
    r'\b(?:Text|SourceEmptyHint|setContentTitle|setContentText)\s*\(\s*'
    r'(?:text\s*=\s*)?"([^"]+)"', re.MULTILINE,
)
DISPLAY_PROP = re.compile(
    r'\b(?:serviceName|label|contentDescription|title)\s*=\s*"([^"]+)"',
    re.MULTILINE,
)
VIOLATIONS: list[str] = []

def fail(path: Path, location: str, display: str) -> None:
    VIOLATIONS.append(f"{path.relative_to(ROOT)}:{location}: {display[:140]}")

# Scan text values, not resource *keys*. Historical IDs must stay stable.
for xml in ROOT.glob("**/src/main/res/values*/strings.xml"):
    try:
        tree = ET.parse(xml)
    except ET.ParseError as exc:
        fail(xml, "xml", f"Malformed resources: {exc}")
        continue
    for node in tree.getroot():
        if node.tag not in ("string", "plurals", "string-array"):
            continue
        value = "".join(node.itertext())
        match = LEGACY.search(value)
        if match:
            fail(xml, node.get("name", "?"), match.group(0))

# Scan literal UI-facing strings; comments, persisted field names and package
# names are intentionally ignored. Broader visual QA remains mandatory.
roots = [ROOT / "app/src/main/kotlin", ROOT / "core/ui/src/main/kotlin"]
roots += list((ROOT / "feature").glob("*/src/main/kotlin"))
for base in roots:
    if not base.is_dir():
        continue
    for source in base.rglob("*.kt"):
        contents = source.read_text(encoding="utf-8")
        for pattern in (DISPLAY_CALL, DISPLAY_PROP):
            for match in pattern.finditer(contents):
                found = LEGACY.search(match.group(1))
                if found:
                    line = contents.count("\n", 0, match.start()) + 1
                    fail(source, str(line), match.group(1))

# Guard against accidentally retaining user-selectable excluded sources.
stream = (ROOT / "core/media/src/main/kotlin/com/stash/core/media/streaming/StreamSourceRegistry.kt").read_text()
start = stream.find("val resolvers =")
end = stream.find("val hedged =", start)
if start == -1 or end <= start:
    VIOLATIONS.append("StreamSourceRegistry: approved resolver list could not be inspected")
else:
    roster = stream[start:end]
    allowed = re.findall(r'add\("([^"]+)"\s+to', roster)
    if allowed != ["youtube"]:
        VIOLATIONS.append(f"StreamSourceRegistry: expected YouTube-only chain, got {allowed!r}")
lossless = (ROOT / "data/download/src/main/kotlin/com/stash/data/download/lossless/LosslessSourceRegistry.kt").read_text()
if not re.search(r"private suspend fun chain\(\): List<LosslessSource>\s*=\s*emptyList\(\)", lossless):
    VIOLATIONS.append("LosslessSourceRegistry: excluded music providers may be reachable")
home = (ROOT / "feature/home/src/main/kotlin/com/stash/feature/home/HomeViewModel.kt").read_text()
begin = home.find("private val discoveryFlow:")
finish = home.find("/** Select a genre chip", begin)
if begin < 0 or finish < begin or re.search(r"homeDiscoveryRepository\.", home[begin:finish]):
    VIOLATIONS.append("HomeViewModel: excluded Qobuz discovery fetch is reachable")
gradle = (ROOT / "app/build.gradle.kts").read_text()
if 'buildConfigField("Boolean", "WEB_LINK_ENABLED", "true")' in gradle:
    VIOLATIONS.append("app/build.gradle.kts: unsupported external Stash web sync is enabled")

# Excluded music catalogs must also be unreachable from artist/album flows,
# background discovery, and restored strict-lossless download preferences.
weekly = (ROOT / "feature/home/src/main/kotlin/com/stash/feature/home/SonoraWeeklyDiscoveryEngine.kt").read_text()
if re.search(r"lastfm\s*\.\s*get(?:TagTopTracks|ArtistTopTags)", weekly):
    VIOLATIONS.append("SonoraWeeklyDiscoveryEngine: active Last.fm catalog request")
discography = (ROOT / "data/download/src/main/kotlin/com/stash/data/download/lossless/qbdlx/QobuzDiscographyProvider.kt").read_text()
active_merge = discography.split("override suspend fun mergeInto(", 1)[-1].split("private fun unchanged(", 1)[0]
if "apiClient." in active_merge:
    VIOLATIONS.append("QobuzDiscographyProvider: restored artist route may query Qobuz")
album_cache = (ROOT / "core/data/src/main/kotlin/com/stash/core/data/cache/AlbumCache.kt").read_text()
if re.search(r"AlbumSource\.QOBUZ(?:_PLAYLIST)?\s*->\s*qobuzFetcher\.", album_cache):
    VIOLATIONS.append("AlbumCache: restored Qobuz album route may call excluded provider")
prefs = (ROOT / "data/download/src/main/kotlin/com/stash/data/download/lossless/LosslessSourcePreferences.kt").read_text()
if "val enabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)" not in prefs:
    VIOLATIONS.append("LosslessSourcePreferences: old strict-FLAC flag may block downloads")
if "val youtubeFallbackEnabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)" not in prefs:
    VIOLATIONS.append("LosslessSourcePreferences: YouTube fallback can be disabled by a stale flag")
if "https://stash-tipjar.rawnaldclark.workers.dev" in gradle:
    VIOLATIONS.append("app/build.gradle.kts: obsolete supporter server remains configured")

if VIOLATIONS:
    print("SONORA IDENTITY GATE FAILED:")
    for item in sorted(set(VIOLATIONS)):
        print(f"  - {item}")
    sys.exit(1)
print("Sonora identity gate passed: visible strings and excluded source routing checked.")
