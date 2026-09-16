ALTER TABLE lyna.account_discord_link
	ADD COLUMN IF NOT EXISTS handle           TEXT      NULL,
	ADD COLUMN IF NOT EXISTS handle_seen_at   TIMESTAMP NULL;
