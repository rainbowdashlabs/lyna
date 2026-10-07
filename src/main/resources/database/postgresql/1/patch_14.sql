CREATE TABLE lyna.release_webhook (
	product_id INTEGER NOT NULL PRIMARY KEY
		REFERENCES lyna.product (id) ON DELETE CASCADE,
	token      TEXT    NOT NULL UNIQUE,
	secret     TEXT    NOT NULL,
	channel_id BIGINT
);

COMMENT ON TABLE lyna.release_webhook IS
	'Where GitHub reports a product''s releases, and the channel they are announced in. The secret signs what GitHub sends; the token is the address.';
