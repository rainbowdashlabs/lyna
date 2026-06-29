CREATE TABLE lyna.password_reset_token (
	token_hash  TEXT      NOT NULL PRIMARY KEY,
	account_id  INTEGER   NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	created_at  TIMESTAMP NOT NULL DEFAULT now(),
	expires_at  TIMESTAMP NOT NULL
);

CREATE INDEX password_reset_token_account_id_idx
	ON lyna.password_reset_token (account_id);
CREATE INDEX password_reset_token_expires_at_idx
	ON lyna.password_reset_token (expires_at);
