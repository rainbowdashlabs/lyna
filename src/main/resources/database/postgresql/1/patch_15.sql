CREATE TABLE lyna.mail_guild_settings (
	guild_id    BIGINT NOT NULL PRIMARY KEY,
	blocks      JSONB  NULL,
	discord_url TEXT   NULL,
	links       JSONB  NOT NULL DEFAULT '[]'::JSONB
);

COMMENT ON COLUMN lyna.mail_guild_settings.blocks IS
	'The purchase mail every product without its own body sends; null means the one the application ships.';
COMMENT ON COLUMN lyna.mail_guild_settings.links IS
	'Links the links block renders as a row, as an array of {label, url}.';

ALTER TABLE lyna.product
	ADD COLUMN wiki_url   TEXT NULL,
	ADD COLUMN review_url TEXT NULL;
