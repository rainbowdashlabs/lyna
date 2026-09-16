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
					INSERT INTO lyna.account (email, password_hash)
					VALUES (NULL, NULL)
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
