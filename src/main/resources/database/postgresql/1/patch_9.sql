CREATE TABLE lyna.download_log (
	id            BIGSERIAL PRIMARY KEY,
	account_id    INTEGER   NULL REFERENCES lyna.account (id) ON DELETE SET NULL,
	discord_id    BIGINT    NULL,
	license_id    INTEGER   NULL REFERENCES lyna.license (id) ON DELETE SET NULL,
	product_id    INTEGER   NOT NULL REFERENCES lyna.product (id) ON DELETE CASCADE,
	download_id   INTEGER   NOT NULL REFERENCES lyna.download (id) ON DELETE CASCADE,
	version       TEXT      NOT NULL,
	source        TEXT      NOT NULL,
	downloaded_at TIMESTAMP NOT NULL DEFAULT now(),
	user_agent    TEXT      NULL,
	ip_hash       TEXT      NULL
);

CREATE INDEX download_log_account_id_downloaded_at_idx
	ON lyna.download_log (account_id, downloaded_at DESC);
CREATE INDEX download_log_license_id_downloaded_at_idx
	ON lyna.download_log (license_id, downloaded_at DESC);
CREATE INDEX download_log_product_id_downloaded_at_idx
	ON lyna.download_log (product_id, downloaded_at DESC);


CREATE TABLE lyna.account_session (
	jti           TEXT      NOT NULL PRIMARY KEY,
	account_id    INTEGER   NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	issued_at     TIMESTAMP NOT NULL DEFAULT now(),
	expires_at    TIMESTAMP NOT NULL,
	last_seen_at  TIMESTAMP NULL,
	user_agent    TEXT      NULL
);

CREATE INDEX account_session_account_id_idx
	ON lyna.account_session (account_id);
CREATE INDEX account_session_expires_at_idx
	ON lyna.account_session (expires_at);
