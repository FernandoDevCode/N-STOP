#!/usr/bin/env python3
"""Probe podbox Art Fetch's UNMODIFIED gallery extractor (no APK changes)."""
import hashlib
import importlib.util
import json
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse

import requests

UPSTREAM_COMMIT = "e54f9f05715c06bfe39c3cf7be0a0c44aeed94be"
UPSTREAM_BLOB = "6e1c5bf06ecb8fe03e1c7dd7b7878f7b92b1d6de"
UPSTREAM_URL = ("https://raw.githubusercontent.com/anthonyfletcher/podbox/" +
                UPSTREAM_COMMIT + "/tools/art_fetch/art_fetch.py")
NAMES = ("Slipknot", "Radiohead", "System of a Down")

def load_upstream():
    response = requests.get(UPSTREAM_URL, timeout=20)
    response.raise_for_status()
    blob = response.content
    digest = hashlib.sha1(b"blob " + str(len(blob)).encode() + b"\x00" + blob).hexdigest()
    if digest != UPSTREAM_BLOB:
        raise RuntimeError("Unexpected upstream source blob: " + digest)
    import sys
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "art_fetch.py"
        path.write_bytes(blob)
        spec = importlib.util.spec_from_file_location("podbox_art_fetch", path)
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
    return module

class RecordingSession(requests.Session):
    def __init__(self):
        super().__init__()
        self.events = []
    def get(self, url, **kwargs):
        response = super().get(url, **kwargs)
        title = re.search(r"<title[^>]*>(.*?)</title>", response.text[:12000], re.I | re.S)
        title = re.sub(r"\s+", " ", title.group(1)).strip() if title else "(none)"
        self.events.append({
            "url":response.url,
            "status":response.status_code,
            "bytes":len(response.content),
            "title":title[:120],
            "challenge":("client challenge" in title.lower()),
            "gallery_marked_popular": "sorted by: most popular" in response.text.lower(),
            "gallery_photo_hashes":len(set(re.findall(r"/\+images/([a-f0-9]{32})",response.text,re.I))),
        })
        return response

class NoopLimiter:
    def wait(self,service):
        pass

mod=load_upstream()
result = {
    "project": "anthonyfletcher/podbox tools/art_fetch/art_fetch.py",
    "upstream_commit": UPSTREAM_COMMIT,
    "upstream_blob_verified": True,
    "function": "lookup_lastfm_artist_web_candidates",
    "api_key": False,
    "login": False,
    "artists": [],
}
for name in NAMES:
    session = RecordingSession()
    try:
        candidates = mod.lookup_lastfm_artist_web_candidates(
            name,session,NoopLimiter()
        )
        unique_hashes = list(dict.fromkeys(
            re.search(r"/([0-9a-f]{32})(?:\.|$)",c.image_url,re.I).group(1)
            for c in candidates
            if re.search(r"/([0-9a-f]{32})(?:\.|$)",c.image_url,re.I)
        ))
        entry={
            "artist": name,
            "image_candidate_urls":len(candidates),
            "unique_image_hashes":len(unique_hashes),
            "sample_image_urls":[c.image_url for c in candidates[:5]],
            "sample_photo_hashes": unique_hashes[:6],
            "requests":session.events,
            "gallery_success":any(e["gallery_photo_hashes"] and e["gallery_marked_popular"] for e in session.events),
        }
    except Exception as e:
        entry={"artist":name,"error":str(e)[:200],"requests":session.events}
    finally:
        session.close()
    result["artists"].append(entry)
    print(name+" | requests="+str(len(entry["requests"]))+
          " | candidates="+str(entry.get("image_candidate_urls",0))+
          " | unique_photo_hashes="+str(entry.get("unique_image_hashes",0)),flush=True)
    for event in entry["requests"]:
        print("   HTTP "+str(event["status"])+", bytes="+str(event["bytes"])+
              ", title="+event["title"]+
              ", photo_hashes="+str(event["gallery_photo_hashes"])+
              ", popular="+str(event["gallery_marked_popular"]),flush=True)
# Verify a small sample of distinct candidate photos; no ranking claims.
from io import BytesIO
from PIL import Image

for entry in result["artists"]:
    images=[]
    for hash_id in entry.get("sample_photo_hashes",[])[:2]:
        image_url="https://lastfm-img.freetls.fastly.net/i/u/ar0/"+hash_id+".jpg"
        try:
            response=requests.get(image_url, timeout=(7,14))
            response.raise_for_status()
            if len(response.content)>5_000_000:
                raise RuntimeError("Image exceeds 5MB safety limit")
            img=Image.open(BytesIO(response.content))
            img.verify()
            img=Image.open(BytesIO(response.content))
            images.append({"url":image_url,"http":response.status_code,
                           "width":img.width,"height":img.height,
                           "format":img.format,"bytes":len(response.content)})
        except Exception as exc:
            images.append({"url":image_url,"error":str(exc)[:120]})
    entry["validated_original_samples"]=images
    for item in images:
        print("   PHOTO "+str(item),flush=True)

print("BEGIN_PODBOX_JSON")
print(json.dumps(result,indent=2,ensure_ascii=False))
print("END_PODBOX_JSON")
