CREATE TABLE lyna.account (
	id              SERIAL    PRIMARY KEY,
	email           TEXT      NULL,
	password_hash   TEXT      NULL,
	theme           TEXT      NULL,
	feel            TEXT      NULL,
	dark_mode       TEXT      NULL,
	created_at      TIMESTAMP NOT NULL DEFAULT now(),
	last_login_at   TIMESTAMP NULL
);

CREATE UNIQUE INDEX account_lower_email_uindex
	ON lyna.account (LOWER(email))
	WHERE email IS NOT NULL;

CREATE TABLE lyna.account_discord_link (
	account_id      INTEGER   NOT NULL PRIMARY KEY
		REFERENCES lyna.account (id) ON DELETE CASCADE,
	discord_user_id BIGINT    NOT NULL UNIQUE,
	linked_at       TIMESTAMP NOT NULL DEFAULT now(),
	verified_via    TEXT      NOT NULL
);

CREATE INDEX account_discord_link_discord_user_id_index
	ON lyna.account_discord_link (discord_user_id);

CREATE TABLE lyna.revoked_jti (
	jti         TEXT      NOT NULL PRIMARY KEY,
	revoked_at  TIMESTAMP NOT NULL DEFAULT now(),
	expires_at  TIMESTAMP NOT NULL
);

CREATE INDEX revoked_jti_expires_at_index
	ON lyna.revoked_jti (expires_at);

CREATE TABLE lyna.instance_settings (
	id                   INTEGER   NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),
	default_theme        TEXT      NOT NULL DEFAULT 'ember',
	default_feel         TEXT      NOT NULL DEFAULT 'ROUNDED',
	lock_feel            BOOLEAN   NOT NULL DEFAULT FALSE,
	allow_user_theme     BOOLEAN   NOT NULL DEFAULT TRUE,
	allow_user_feel      BOOLEAN   NOT NULL DEFAULT TRUE,
	enabled_themes       TEXT[]    NOT NULL DEFAULT ARRAY[]::TEXT[],
	custom_theme_colors  JSONB     NULL
);

INSERT INTO lyna.instance_settings (id) VALUES (1) ON CONFLICT DO NOTHING;
