-- Per-network daily budgets for new shared mixes and song links (src/quota.js). network is sha256(COMMUNITY_SALT | IPv4,
-- or IPv6 /56), like posts.ip_hash; day is the UTC day number (ms / 86400000). The daily cron deletes past days.
CREATE TABLE share_quota (
  network  TEXT NOT NULL,
  kind     TEXT NOT NULL CHECK (kind IN ('mix', 'track')),
  day      INTEGER NOT NULL,
  n        INTEGER NOT NULL,
  PRIMARY KEY (network, kind, day)
);
