# Last.fm gallery: retry/session experiment

Run: GitHub Actions. 3 artists, at most 4 GETs per artist, 2/4/8 second pauses.
Attempts 1–3 reuse one requests.Session; attempt 4 uses a fresh Session.
No login, cookies supplied, cache library, CAPTCHA solving or proxy rotation.
Python requests.Session does **not** have a persistent cache to clear.
HTTP 200 is counted as success **only** when real gallery image links are present.
HTTP 403 or 429 and identifiable challenge pages stop retries.

| Artist | Attempt | Session | HTTP | HTML bytes | Photo links | Popularity label | Cache evidence | Result |
|---|---:|---|---:|---:|---:|---|---|---|
| Slipknot | 1 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
<!-- Slipknot attempt 1 title: Client Challenge -->
| Slipknot | 2 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| Slipknot | 3 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| Slipknot | 4 | fresh | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |

Slipknot: ranked gallery not obtained.

| Radiohead | 1 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
<!-- Radiohead attempt 1 title: Client Challenge -->
| Radiohead | 2 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| Radiohead | 3 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| Radiohead | 4 | fresh | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |

Radiohead: ranked gallery not obtained.

| System of a Down | 1 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
<!-- System of a Down attempt 1 title: Client Challenge -->
| System of a Down | 2 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| System of a Down | 3 | reused | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |
| System of a Down | 4 | fresh | 200 | 3038 | 0 | no | MISS | INVALID: no gallery in HTML |

System of a Down: ranked gallery not obtained.

