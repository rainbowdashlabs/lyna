CREATE TABLE lyna.instance_operator (
	discord_id BIGINT    NOT NULL PRIMARY KEY,
	added_by   BIGINT    NULL,
	added_at   TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE lyna.license_settings
	ADD COLUMN IF NOT EXISTS admin_role_id BIGINT NULL;
