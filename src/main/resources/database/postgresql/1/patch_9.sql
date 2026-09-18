-- What a product says about itself on its own page. Markdown, written by an operator and rendered
-- where it is shown.
ALTER TABLE lyna.product
	ADD COLUMN IF NOT EXISTS description TEXT NULL;
