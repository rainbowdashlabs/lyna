CREATE TABLE lyna.debug_report (
	id              SERIAL    NOT NULL PRIMARY KEY,
	read_key        TEXT      NOT NULL UNIQUE,
	delete_key_hash TEXT      NOT NULL UNIQUE,
	created         TIMESTAMP NOT NULL DEFAULT now(),
	plugin_name     TEXT      NOT NULL,
	plugin_version  TEXT      NOT NULL,
	plugin_meta     JSONB     NOT NULL,
	server_meta     JSONB     NOT NULL
);

CREATE INDEX debug_report_created_index
	ON lyna.debug_report (created);

COMMENT ON TABLE lyna.debug_report IS
	'A debug report a plugin uploaded. Found by its read key; removed by whoever holds the delete key, or when it expires.';

CREATE TABLE lyna.debug_report_entry (
	report_id INTEGER NOT NULL
		REFERENCES lyna.debug_report (id) ON DELETE CASCADE,
	position  INTEGER NOT NULL,
	kind      TEXT    NOT NULL
		CHECK (kind IN ('LOG', 'PLUGIN_LOG', 'INTERNAL_EXCEPTION', 'EXTERNAL_EXCEPTION', 'CONFIG', 'META')),
	name      TEXT    NOT NULL,
	content   TEXT    NOT NULL,
	PRIMARY KEY (report_id, position)
);

COMMENT ON TABLE lyna.debug_report_entry IS
	'One section of a debug report: a log, an exception, a config file or a block of plugin metadata, each loaded on its own.';
