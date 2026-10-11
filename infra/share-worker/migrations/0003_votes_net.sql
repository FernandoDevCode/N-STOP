-- Votes by network within a post: the vote INSERT counts a network's rows for the post (VOTE_ROWS_PER_NETWORK), and the
-- recount groups each direction by ip_hash. Without it both walk every vote row of the post.
CREATE INDEX votes_net ON votes (post_id, ip_hash, value);
