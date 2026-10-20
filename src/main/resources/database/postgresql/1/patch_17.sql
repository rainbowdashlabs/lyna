CREATE TABLE lyna.account_role (
	account_id INTEGER     NOT NULL,
	guild_id   BIGINT      NOT NULL,
	role_id    BIGINT      NOT NULL,
	source     TEXT        NOT NULL,
	synced_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT account_role_pk PRIMARY KEY (account_id, role_id, source),
	CONSTRAINT account_role_account_id_fk
		FOREIGN KEY (account_id) REFERENCES lyna.account (id)
			ON DELETE CASCADE
);

CREATE INDEX account_role_role_id_index
	ON lyna.account_role (role_id);

COMMENT ON TABLE lyna.account_role IS
	'Roles an account wears, so access by role is answered without asking Discord.';
COMMENT ON COLUMN lyna.account_role.source IS
	'DISCORD for roles mirrored from the guild, which every sync replaces.';
