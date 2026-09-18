CREATE TABLE lyna.account (
	id            SERIAL    PRIMARY KEY,
	password_hash TEXT      NULL,
	theme         TEXT      NULL,
	dark_mode     TEXT      NULL,
	created_at    TIMESTAMP NOT NULL DEFAULT now(),
	last_login_at TIMESTAMP NULL,
	username      TEXT      NULL,
	discriminator TEXT      NULL,
	CONSTRAINT account_discriminator_is_four_digits
		CHECK (discriminator IS NULL OR discriminator ~ '^[0-9]{4}$')
);

CREATE UNIQUE INDEX account_username_unique
	ON lyna.account (lower(username), COALESCE(discriminator, ''))
	WHERE username IS NOT NULL;

CREATE TABLE lyna.account_email (
	account_id  INTEGER   NOT NULL
		REFERENCES lyna.account (id) ON DELETE CASCADE,
	email       TEXT      NOT NULL,
	verified_at TIMESTAMP NULL,
	added_at    TIMESTAMP NOT NULL DEFAULT now(),
	is_primary  BOOLEAN   NOT NULL DEFAULT FALSE,
	CONSTRAINT account_email_pk PRIMARY KEY (account_id, email)
);

-- An address belongs to one account, once it is proved or once it is the address an account is
-- written to. Both are things somebody can be found by, so both have to name one person.
--
-- A merely claimed address is deliberately not exclusive: reserving one on the strength of somebody
-- typing it would let anyone take an address they do not own away from whoever does.
CREATE UNIQUE INDEX account_email_unique
	ON lyna.account_email (LOWER(email))
	WHERE verified_at IS NOT NULL OR is_primary;

-- The one the application sends to.
CREATE UNIQUE INDEX account_email_one_primary
	ON lyna.account_email (account_id)
	WHERE is_primary;

CREATE INDEX account_email_account_id_index
	ON lyna.account_email (account_id);

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

CREATE TABLE lyna.account_session (
	jti          TEXT      NOT NULL PRIMARY KEY,
	account_id   INTEGER   NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	issued_at    TIMESTAMP NOT NULL DEFAULT now(),
	expires_at   TIMESTAMP NOT NULL,
	last_seen_at TIMESTAMP NULL,
	user_agent   TEXT      NULL
);

CREATE INDEX account_session_account_id_idx
	ON lyna.account_session (account_id);
CREATE INDEX account_session_expires_at_idx
	ON lyna.account_session (expires_at);

CREATE TABLE lyna.revoked_jti (
	jti        TEXT      NOT NULL PRIMARY KEY,
	revoked_at TIMESTAMP NOT NULL DEFAULT now(),
	expires_at TIMESTAMP NOT NULL
);

CREATE INDEX revoked_jti_expires_at_index
	ON lyna.revoked_jti (expires_at);

CREATE TABLE lyna.password_reset_token (
	token_hash TEXT      NOT NULL PRIMARY KEY,
	account_id INTEGER   NOT NULL REFERENCES lyna.account (id) ON DELETE CASCADE,
	created_at TIMESTAMP NOT NULL DEFAULT now(),
	expires_at TIMESTAMP NOT NULL
);

CREATE INDEX password_reset_token_account_id_idx
	ON lyna.password_reset_token (account_id);
CREATE INDEX password_reset_token_expires_at_idx
	ON lyna.password_reset_token (expires_at);

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

CREATE TABLE lyna.instance_settings (
	id                  INTEGER NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),
	default_theme       TEXT    NOT NULL DEFAULT 'transistor',
	allow_user_theme    BOOLEAN NOT NULL DEFAULT TRUE,
	enabled_themes      TEXT[]  NOT NULL DEFAULT ARRAY[]::TEXT[],
	custom_theme_colors JSONB   NULL
);

INSERT INTO lyna.instance_settings (id) VALUES (1) ON CONFLICT DO NOTHING;

CREATE TABLE lyna.instance_operator (
	discord_id BIGINT    NOT NULL PRIMARY KEY,
	added_by   BIGINT    NULL,
	added_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE lyna.demo_artifact (
	kind       TEXT      NOT NULL,
	artifact   TEXT      NOT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT now(),
	CONSTRAINT demo_artifact_pk PRIMARY KEY (kind, artifact)
);

CREATE TABLE lyna.license_invite (
	license_id INTEGER   NOT NULL
		REFERENCES lyna.license (id) ON DELETE CASCADE,
	email      TEXT      NOT NULL,
	invited_at TIMESTAMP NOT NULL DEFAULT now(),
	expires_at TIMESTAMP NOT NULL,
	CONSTRAINT license_invite_pk PRIMARY KEY (license_id, email)
);

CREATE INDEX license_invite_email_index
	ON lyna.license_invite (lower(email));

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

ALTER TABLE lyna.product
	ADD COLUMN IF NOT EXISTS icon_url TEXT NULL;

ALTER TABLE lyna.license_settings
	ADD COLUMN IF NOT EXISTS admin_role_id BIGINT NULL;

ALTER TABLE lyna.mail_products
	ADD COLUMN IF NOT EXISTS blocks JSONB NULL;

-- Where a licence came from, which used to be a prefix on the identifier of whoever bought it.
ALTER TABLE lyna.license
	ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'MANUAL';

UPDATE lyna.license
SET source          = 'KOFI',
    user_identifier = substring(user_identifier FROM 6)
WHERE user_identifier LIKE 'kofi:%';

ALTER TABLE lyna.license
	ADD CONSTRAINT license_source_known
		CHECK (source IN ('KOFI', 'MAIL', 'MANUAL'));

CREATE INDEX license_source_identifier_index
	ON lyna.license (source, LOWER(user_identifier));

-- A licence is held by an account rather than by a Discord user. Everyone holding one now is known
-- only by their Discord id, so each gets an account carrying that id as an identity.
DROP VIEW IF EXISTS lyna.user_product_access;
DROP VIEW IF EXISTS lyna.user_guild_license;
DROP VIEW IF EXISTS lyna.user_guild_sub_license;
DROP VIEW IF EXISTS lyna.user_license_all;
DROP VIEW IF EXISTS lyna.user_products_all;
DROP VIEW IF EXISTS lyna.user_products;

ALTER TABLE lyna.user_license
	ADD COLUMN IF NOT EXISTS account_id INTEGER NULL
		REFERENCES lyna.account (id) ON DELETE CASCADE;

ALTER TABLE lyna.user_sub_license
	ADD COLUMN IF NOT EXISTS account_id INTEGER NULL
		REFERENCES lyna.account (id) ON DELETE CASCADE;

DO
$$
	DECLARE
		holder      BIGINT;
		new_account INTEGER;
	BEGIN
		FOR holder IN
			SELECT user_id FROM lyna.user_license
			UNION
			SELECT user_id FROM lyna.user_sub_license
			LOOP
				IF NOT EXISTS (SELECT 1
				               FROM lyna.account_identity
				               WHERE provider = 'discord' AND external_id = holder::TEXT) THEN
					INSERT INTO lyna.account (password_hash)
					VALUES (NULL)
					RETURNING id INTO new_account;

					INSERT INTO lyna.account_identity (provider, external_id, account_id, verified_via)
					VALUES ('discord', holder::TEXT, new_account, 'migrated');
				END IF;
			END LOOP;
	END
$$;

UPDATE lyna.user_license u
SET account_id = i.account_id
FROM lyna.account_identity i
WHERE i.provider = 'discord' AND i.external_id = u.user_id::TEXT;

UPDATE lyna.user_sub_license u
SET account_id = i.account_id
FROM lyna.account_identity i
WHERE i.provider = 'discord' AND i.external_id = u.user_id::TEXT;

ALTER TABLE lyna.user_license
	ALTER COLUMN account_id SET NOT NULL,
	DROP COLUMN user_id;

ALTER TABLE lyna.user_sub_license
	DROP CONSTRAINT IF EXISTS user_sub_license_pk;

ALTER TABLE lyna.user_sub_license
	ALTER COLUMN account_id SET NOT NULL,
	DROP COLUMN user_id;

ALTER TABLE lyna.user_sub_license
	ADD CONSTRAINT user_sub_license_pk PRIMARY KEY (account_id, license_id);

CREATE INDEX IF NOT EXISTS user_license_account_id_index
	ON lyna.user_license (account_id);

CREATE OR REPLACE VIEW lyna.user_products AS
SELECT p.guild_id, i.external_id::BIGINT AS user_id, p.id, p.name, p.url, p.role
FROM lyna.user_license u
	     JOIN lyna.account_identity i
	          ON i.account_id = u.account_id AND i.provider = 'discord'
	     LEFT JOIN lyna.license l ON u.license_id = l.id
	     LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE OR REPLACE VIEW lyna.user_products_all AS
SELECT DISTINCT (p.name), p.guild_id, i.external_id::BIGINT AS user_id, p.id, p.url, p.role
FROM (SELECT account_id, license_id
      FROM lyna.user_license
      UNION
      SELECT account_id, license_id
      FROM lyna.user_sub_license) u
	     JOIN lyna.account_identity i
	          ON i.account_id = u.account_id AND i.provider = 'discord'
	     LEFT JOIN lyna.license l ON u.license_id = l.id
	     LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE OR REPLACE VIEW lyna.user_license_all AS
SELECT p.guild_id,
       i.external_id::BIGINT AS user_id,
       l.product_id,
       u.license_id,
       l.user_identifier,
       l.key
FROM (SELECT account_id, license_id
      FROM lyna.user_license
      UNION
      SELECT account_id, license_id
      FROM lyna.user_sub_license) u
	     JOIN lyna.account_identity i
	          ON i.account_id = u.account_id AND i.provider = 'discord'
	     LEFT JOIN lyna.license l ON u.license_id = l.id
	     LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE OR REPLACE VIEW lyna.user_product_access AS
SELECT l.user_id, a.release_type, l.product_id
FROM lyna.user_license_all l
	     LEFT JOIN lyna.license_access a ON l.license_id = a.license_id;

CREATE OR REPLACE VIEW lyna.user_guild_license AS
SELECT l.product_id,
       l.user_identifier,
       l.id,
       l.key,
       i.external_id::BIGINT AS user_id,
       p.guild_id
FROM lyna.license l
	     LEFT JOIN lyna.user_license u ON l.id = u.license_id
	     LEFT JOIN lyna.account_identity i
	               ON i.account_id = u.account_id AND i.provider = 'discord'
	     LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE OR REPLACE VIEW lyna.user_guild_sub_license AS
SELECT l.product_id,
       l.user_identifier,
       l.id,
       l.key,
       i.external_id::BIGINT AS user_id,
       p.guild_id
FROM lyna.license l
	     LEFT JOIN lyna.user_sub_license u ON l.id = u.license_id
	     LEFT JOIN lyna.account_identity i
	               ON i.account_id = u.account_id AND i.provider = 'discord'
	     LEFT JOIN lyna.product p ON l.product_id = p.id;
