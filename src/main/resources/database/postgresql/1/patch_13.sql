ALTER TABLE lyna.debug_report_entry
	DROP CONSTRAINT debug_report_entry_kind_check,
	ADD CONSTRAINT debug_report_entry_kind_check
		CHECK (kind IN ('LOG', 'PLUGIN_LOG', 'INTERNAL_EXCEPTION', 'EXTERNAL_EXCEPTION', 'CONFIG', 'META', 'CHECK')),
	ADD COLUMN format      TEXT
		CHECK (format IN ('yaml', 'json', 'properties', 'toml', 'log', 'text')),
	ADD COLUMN level       TEXT
		CHECK (level IN ('OK', 'WARN', 'ERROR')),
	ADD COLUMN occurrences INTEGER
		CHECK (occurrences > 0),
	ADD COLUMN first_seen  TEXT,
	ADD COLUMN last_seen   TEXT,
	ADD COLUMN entries     JSONB,
	ADD CONSTRAINT debug_report_entry_check_level
		CHECK ((kind = 'CHECK') = (level IS NOT NULL));

COMMENT ON COLUMN lyna.debug_report_entry.format IS
	'How the content is highlighted; guessed from the name when null.';
COMMENT ON COLUMN lyna.debug_report_entry.level IS
	'The outcome of a health check the plugin ran; set exactly on CHECK sections.';
COMMENT ON COLUMN lyna.debug_report_entry.occurrences IS
	'How often an exception occurred in the log, with first_seen and last_seen as the log times of the first and last.';
COMMENT ON COLUMN lyna.debug_report_entry.entries IS
	'Key and value pairs of a META section, as an array of {key, value}, shown as a table.';
