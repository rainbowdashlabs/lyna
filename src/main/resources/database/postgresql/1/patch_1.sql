CREATE TYPE lyna.RELEASE_TYPE AS ENUM ('STABLE', 'DEV', 'SNAPSHOT');

CREATE TABLE lyna.download_type (
	id           SERIAL
		CONSTRAINT download_type_pk
			PRIMARY KEY,
	guild_id     BIGINT            NOT NULL,
	name         TEXT              NOT NULL,
	description  TEXT              NOT NULL,
	release_type lyna.RELEASE_TYPE NOT NULL
);

CREATE UNIQUE INDEX download_type_guild_id_lower_name_uindex
	ON lyna.download_type (guild_id, lower(name));


CREATE TABLE lyna.download (
	product_id  INTEGER NOT NULL
		CONSTRAINT download_product_id_fk
			REFERENCES lyna.product
			ON DELETE CASCADE,
	type_id     INTEGER NOT NULL
		CONSTRAINT download_download_type_id_fk
			REFERENCES lyna.download_type,
	repository  TEXT    NOT NULL,
	group_id    TEXT    NOT NULL,
	artifact_id TEXT    NOT NULL,
	classifier  TEXT
);

CREATE UNIQUE INDEX download_product_id_type_id_uindex
	ON lyna.download (product_id, type_id);

CREATE TABLE lyna.role_access (
	role_id      BIGINT            NOT NULL,
	product_id   INT               NOT NULL
		CONSTRAINT role_access_product_id_fk
			REFERENCES lyna.product
			ON DELETE CASCADE,
	release_type lyna.RELEASE_TYPE NOT NULL
);

CREATE UNIQUE INDEX role_access_role_id_product_id_release_type_uindex
	ON lyna.role_access (role_id, product_id, release_type);

CREATE TABLE lyna.license_access (
	license_id   INTEGER           NOT NULL
		CONSTRAINT license_access_license_id_fk
			REFERENCES lyna.license
			ON DELETE CASCADE,
	release_type lyna.RELEASE_TYPE NOT NULL
);

CREATE UNIQUE INDEX license_access_license_id_release_type_uindex
	ON lyna.license_access (license_id, release_type);

INSERT
INTO
	lyna.license_access(license_id, release_type)
SELECT
	id,
	'STABLE'::lyna.RELEASE_TYPE
FROM
	lyna.license;

CREATE VIEW lyna.user_license_all AS
SELECT guild_id,  user_id, product_id, platform_id, license_id, user_identifier, key
FROM
	(
		SELECT
			license_id,
			user_id
		FROM
			lyna.user_license
		UNION
		SELECT
			license_id,
			user_id
		FROM
			lyna.user_sub_license
	) a
		LEFT JOIN lyna.license l
		ON a.license_id = l.id
		LEFT JOIN lyna.product p
		ON l.product_id = p.id;
