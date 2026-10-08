ALTER TABLE lyna.product
	ADD COLUMN page_readme BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN lyna.product.page_readme IS
	'Whether the product page shows the project''s GitHub README instead of the description. A product with no description shows it anyway.';
