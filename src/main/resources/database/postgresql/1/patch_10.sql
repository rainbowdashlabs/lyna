-- An icon a product carries. The images themselves are files on disk; this is what says a product has
-- one, what kind it is, and when it last changed - which is what the serving endpoint answers with.
CREATE TABLE lyna.product_icon (
	product_id INTEGER   NOT NULL PRIMARY KEY
		REFERENCES lyna.product (id) ON DELETE CASCADE,
	mime       TEXT      NOT NULL,
	updated_at TIMESTAMP NOT NULL DEFAULT now()
);
