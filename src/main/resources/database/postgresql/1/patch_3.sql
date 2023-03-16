CREATE TABLE lyna.trial (
	product_id INTEGER NOT NULL
		CONSTRAINT trial_product_id_fk
			REFERENCES lyna.product
			ON DELETE CASCADE,
	user_id    BIGINT  NOT NULL,
	CONSTRAINT trial_pk
		PRIMARY KEY (product_id, user_id)
);

CREATE TABLE lyna.trial_settings (
	guild_id     BIGINT                NOT NULL
		CONSTRAINT trial_settings_pk
			PRIMARY KEY,
	server_time  INTEGER DEFAULT 30    NOT NULL,
	account_time INTEGER DEFAULT 43200 NOT NULL
);

