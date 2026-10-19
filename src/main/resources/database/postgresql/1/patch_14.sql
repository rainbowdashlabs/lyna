ALTER TABLE lyna.account_session
	ADD COLUMN revoked_at TIMESTAMP NULL;

COMMENT ON COLUMN lyna.account_session.revoked_at IS
	'When the session was ended. Its jti stays the same across renewals, so this ends every token it was ever issued.';

UPDATE lyna.account_session s
SET revoked_at = r.revoked_at
FROM lyna.revoked_jti r
WHERE r.jti = s.jti;

DROP TABLE lyna.revoked_jti;

CREATE TABLE lyna.discord_token (
	account_id    INTEGER   NOT NULL PRIMARY KEY
		REFERENCES lyna.account (id) ON DELETE CASCADE,
	access_token  BYTEA     NOT NULL,
	refresh_token BYTEA     NOT NULL,
	scope         TEXT      NOT NULL,
	expires_at    TIMESTAMP NOT NULL,
	refreshed_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX discord_token_expires_at_index
	ON lyna.discord_token (expires_at);

COMMENT ON TABLE lyna.discord_token IS
	'The OAuth grant Discord gave an account at its last sign-in, kept and refreshed so the connection lives as long as Discord allows.';
COMMENT ON COLUMN lyna.discord_token.access_token IS
	'AES-GCM under the key beside the configuration, never in the database; a nonce followed by the ciphertext.';
COMMENT ON COLUMN lyna.discord_token.refresh_token IS
	'Encrypted like access_token. Discord rotates it on every refresh.';
