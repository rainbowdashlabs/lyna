CREATE TABLE lyna.butler_application (
	butler_id  INTEGER NOT NULL PRIMARY KEY,
	product_id INTEGER NOT NULL UNIQUE
		REFERENCES lyna.product (id) ON DELETE CASCADE
);

COMMENT ON TABLE lyna.butler_application IS
	'The id a product had in UpdateButler, which plugins already deployed still ask by.';
