ALTER TABLE lyna.product
	ADD COLUMN parent_id INTEGER,
	ADD CONSTRAINT product_parent_fk
		FOREIGN KEY (parent_id, guild_id) REFERENCES lyna.product (id, guild_id)
			ON DELETE SET NULL (parent_id),
	ADD CONSTRAINT product_parent_not_self
		CHECK (parent_id <> id);

CREATE INDEX product_parent_id_index
	ON lyna.product (parent_id);

COMMENT ON COLUMN lyna.product.parent_id IS
	'The product this one is an add-on of, in the same guild; null for a product that stands on its own. One level only.';
