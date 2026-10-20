CREATE TABLE lyna.update_token (
	id           SERIAL PRIMARY KEY,
	account_id   INTEGER     NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	product_id   INTEGER     NOT NULL REFERENCES lyna.product (id) ON DELETE CASCADE,
	name         TEXT        NOT NULL,
	token        TEXT        NOT NULL UNIQUE,
	created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
	last_used_at TIMESTAMPTZ NULL
);

CREATE INDEX update_token_account_id_product_id_index
	ON lyna.update_token (account_id, product_id);

COMMENT ON TABLE lyna.update_token IS
	'Credentials an updater on somebody''s server trades for a download. The token is replaced on every trade.';

CREATE TABLE lyna.update_token_retired (
	token           TEXT        PRIMARY KEY,
	update_token_id INTEGER     NOT NULL REFERENCES lyna.update_token (id) ON DELETE CASCADE,
	retired_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
	reused_at       TIMESTAMPTZ NULL
);

CREATE INDEX update_token_retired_update_token_id_index
	ON lyna.update_token_retired (update_token_id);

COMMENT ON TABLE lyna.update_token_retired IS
	'Tokens replaced by a trade, kept so that somebody presenting one again is noticed.';

CREATE TABLE lyna.update_token_quota (
	account_id INTEGER NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	product_id INTEGER NOT NULL REFERENCES lyna.product (id) ON DELETE CASCADE,
	granted    INTEGER NOT NULL DEFAULT 0 CHECK (granted >= 0),
	CONSTRAINT update_token_quota_pk PRIMARY KEY (account_id, product_id)
);

COMMENT ON COLUMN lyna.update_token_quota.granted IS
	'Tokens an administrator allowed on top of the default.';

CREATE TABLE lyna.update_token_request (
	id          SERIAL PRIMARY KEY,
	account_id  INTEGER     NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	product_id  INTEGER     NOT NULL REFERENCES lyna.product (id) ON DELETE CASCADE,
	amount      INTEGER     NOT NULL CHECK (amount > 0),
	reason      TEXT        NOT NULL,
	status      TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED')),
	created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
	decided_at  TIMESTAMPTZ NULL,
	decided_by  BIGINT      NULL
);

CREATE UNIQUE INDEX update_token_request_pending_uindex
	ON lyna.update_token_request (account_id, product_id)
	WHERE status = 'PENDING';
