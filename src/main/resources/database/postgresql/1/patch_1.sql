CREATE TABLE lyna.download (
	product_id  INTEGER NOT NULL
		CONSTRAINT download_product_id_fk
			REFERENCES lyna.product
			ON DELETE CASCADE,
	type        TEXT    NOT NULL,
	group_id    TEXT    NOT NULL,
	artifact_id TEXT    NOT NULL,
	classifier  TEXT
);

CREATE UNIQUE INDEX download_product_id_type_uindex
	ON lyna.download (product_id, type);

