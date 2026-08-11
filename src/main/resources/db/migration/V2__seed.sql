-- Demo credentials, and only those. The Pokemon are NOT seeded here: they belong to
-- PokeAPI, and a committed dump of them would be a second copy of upstream's data that
-- goes stale the moment upstream changes and that nothing in this repository can verify.
-- UpstreamSeedRunner replicates them through the ordinary sync path at first boot instead,
-- which also exercises the code the demo is meant to show.
--
-- The hashes are BCrypt at cost 12, never plaintext (WU-999-A J3). They are demonstration
-- credentials for a throwaway database and are documented in quickstart.md; nothing here is
-- a production secret.
INSERT INTO users (username, email, password_hash, roles)
VALUES
    ('demo',  'demo@elatus-dev.com',  '$2a$12$gcM0HjkIdcYUST2YYMR0vuJ8rHraMb/AGOGRKj23mu35BJRsZdGsW', 'CURATOR'),
    ('admin', 'admin@elatus-dev.com', '$2a$12$.PwoUGG15VG5alaGKRZNx.qMROScxwgkuk.B7Q7VpMt26LyvzMxJW', 'CURATOR,ADMIN')
ON CONFLICT (username) DO NOTHING;
