CREATE TABLE lyna.account_identity (
	provider       TEXT      NOT NULL,
	external_id    TEXT      NOT NULL,
	account_id     INTEGER   NOT NULL
		REFERENCES lyna.account (id) ON DELETE CASCADE,
	handle         TEXT      NULL,
	handle_seen_at TIMESTAMP NULL,
	linked_at      TIMESTAMP NOT NULL DEFAULT now(),
	verified_via   TEXT      NOT NULL,
	CONSTRAINT account_identity_pk PRIMARY KEY (provider, external_id),
	CONSTRAINT account_identity_one_per_provider UNIQUE (account_id, provider)
);

CREATE INDEX account_identity_account_id_index
	ON lyna.account_identity (account_id);

INSERT INTO lyna.account_identity (provider, external_id, account_id, handle, handle_seen_at,
                                   linked_at, verified_via)
SELECT 'discord', discord_user_id::TEXT, account_id, handle, handle_seen_at, linked_at, verified_via
FROM lyna.account_discord_link;

DROP TABLE lyna.account_discord_link;
