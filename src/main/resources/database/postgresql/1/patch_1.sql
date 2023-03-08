CREATE TYPE lyna.release_type AS ENUM ('STABLE', 'DEV', 'SNAPSHOT');

CREATE TABLE lyna.download_type (
	id           SERIAL
		CONSTRAINT download_type_pk
			PRIMARY KEY,
	guild_id     BIGINT       NOT NULL,
	name         TEXT         NOT NULL,
	description  TEXT         NOT NULL,
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
