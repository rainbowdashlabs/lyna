ALTER TABLE lyna.account
	ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE lyna.email_verification_token (
	token_hash TEXT      NOT NULL PRIMARY KEY,
	account_id INTEGER   NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	email      TEXT      NOT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT now(),
	expires_at TIMESTAMP NOT NULL
);

CREATE INDEX email_verification_token_account_id_idx
	ON lyna.email_verification_token (account_id);
CREATE INDEX email_verification_token_expires_at_idx
	ON lyna.email_verification_token (expires_at);
