CREATE TABLE lyna.time_channel (
	channel_id BIGINT NOT NULL PRIMARY KEY,
	guild_id   BIGINT NOT NULL,
	zone       TEXT   NOT NULL,
	template   TEXT   NOT NULL
);

COMMENT ON TABLE lyna.time_channel IS
	'A Discord channel renamed every quarter hour to show the time in a zone, such as a developer''s local time.';
