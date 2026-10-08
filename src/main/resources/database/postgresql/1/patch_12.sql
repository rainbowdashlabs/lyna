ALTER TABLE lyna.product
	ADD COLUMN page_readme BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN lyna.product.page_readme IS
	'Whether the product page shows the project''s GitHub README instead of the description. A product with no description shows it anyway.';

ALTER TABLE lyna.trial
	DROP CONSTRAINT trial_pk;

ALTER TABLE lyna.trial
	ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE lyna.trial
	ADD COLUMN account_id INTEGER
		REFERENCES lyna.account (id) ON DELETE CASCADE;

ALTER TABLE lyna.trial
	ADD CONSTRAINT trial_holder CHECK (user_id IS NOT NULL OR account_id IS NOT NULL);

CREATE UNIQUE INDEX trial_product_user_uindex
	ON lyna.trial (product_id, user_id) WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX trial_product_account_uindex
	ON lyna.trial (product_id, account_id) WHERE account_id IS NOT NULL;

COMMENT ON COLUMN lyna.trial.account_id IS
	'The account a trial taken on the web was spent by. A trial taken on Discord has the Discord id in user_id instead; one taken on the web by an account with Discord linked has both, so neither can take it again.';
