ALTER TABLE lyna.download
	ADD id SERIAL NOT NULL;

ALTER TABLE lyna.download
	ADD CONSTRAINT download_pk
		PRIMARY KEY (id);

CREATE TABLE lyna.download_stat (
	download_id INTEGER                  NOT NULL
		CONSTRAINT download_stat_download_id_fk
			REFERENCES lyna.download (id)
			ON DELETE CASCADE,
	version     TEXT                     NOT NULL,
	date        DATE DEFAULT now()::DATE NOT NULL,
	count       INTEGER                  NOT NULL,
	CONSTRAINT download_stat_pk
		PRIMARY KEY (download_id, version, date)
);

