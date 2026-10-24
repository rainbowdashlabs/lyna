CREATE TABLE lyna.product_key_type (
	id              SERIAL  NOT NULL PRIMARY KEY,
	product_id      INTEGER NOT NULL
		REFERENCES lyna.product (id) ON DELETE CASCADE,
	slug            TEXT    NOT NULL CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,31}$'),
	name            TEXT    NOT NULL,
	command         TEXT    NULL,
	enabled         BOOLEAN NOT NULL DEFAULT TRUE,
	refresh_days    INTEGER NOT NULL DEFAULT 7 CHECK (refresh_days > 0),
	expiry_days     INTEGER NOT NULL DEFAULT 30 CHECK (expiry_days > 0),
	allowance       INTEGER NOT NULL DEFAULT 10 CHECK (allowance >= 0),
	trial_allowance INTEGER NOT NULL DEFAULT 1 CHECK (trial_allowance >= 0),
	gateway_secret  TEXT    NOT NULL UNIQUE,
	CONSTRAINT product_key_type_slug_uk UNIQUE (product_id, slug)
);

COMMENT ON TABLE lyna.product_key_type IS
	'A kind of server key a product hands out, such as the key a server proves itself to that product''s hosted dashboard with. Its gateway secret opens its own keys and no others.';
COMMENT ON COLUMN lyna.product_key_type.command IS
	'How a buyer hands a key to their server, with {key} where the key goes. Empty shows the bare key.';

INSERT INTO lyna.product_key_type
	(product_id, slug, name, command, enabled, refresh_days, expiry_days, allowance, gateway_secret)
SELECT p.id,
       'dashboard',
       'Dashboard key',
       '/companyadmin dashboard key {key}',
       p.dashboard_keys,
       p.dashboard_refresh_days,
       p.dashboard_expiry_days,
       p.dashboard_allowance,
       'lyg_' || replace(gen_random_uuid()::TEXT, '-', '') || replace(gen_random_uuid()::TEXT, '-', '')
FROM lyna.product p
WHERE p.dashboard_keys
   OR EXISTS (SELECT 1 FROM lyna.update_token t WHERE t.product_id = p.id AND t.kind = 'DASHBOARD')
   OR EXISTS (SELECT 1 FROM lyna.update_token_quota q WHERE q.product_id = p.id AND q.kind = 'DASHBOARD')
   OR EXISTS (SELECT 1 FROM lyna.update_token_request r WHERE r.product_id = p.id AND r.kind = 'DASHBOARD');

ALTER TABLE lyna.update_token
	DROP CONSTRAINT IF EXISTS update_token_kind_check,
	ADD COLUMN key_type_id INTEGER NULL
		REFERENCES lyna.product_key_type (id) ON DELETE CASCADE;

UPDATE lyna.update_token t
SET kind = 'SERVER', key_type_id = k.id
FROM lyna.product_key_type k
WHERE t.kind = 'DASHBOARD' AND k.product_id = t.product_id AND k.slug = 'dashboard';

ALTER TABLE lyna.update_token
	ADD CONSTRAINT update_token_kind_check CHECK (kind IN ('UPDATE', 'SERVER')),
	ADD CONSTRAINT update_token_key_type_check CHECK ((kind = 'UPDATE') = (key_type_id IS NULL));

COMMENT ON COLUMN lyna.update_token.kind IS
	'UPDATE for an upgrade token an updater trades for a download, SERVER for a key of one of the product''s key types.';

ALTER TABLE lyna.update_token_quota
	DROP CONSTRAINT update_token_quota_pk,
	DROP CONSTRAINT IF EXISTS update_token_quota_kind_check,
	ADD COLUMN key_type_id INTEGER NULL
		REFERENCES lyna.product_key_type (id) ON DELETE CASCADE;

UPDATE lyna.update_token_quota q
SET kind = 'SERVER', key_type_id = k.id
FROM lyna.product_key_type k
WHERE q.kind = 'DASHBOARD' AND k.product_id = q.product_id AND k.slug = 'dashboard';

ALTER TABLE lyna.update_token_quota
	ADD CONSTRAINT update_token_quota_kind_check CHECK (kind IN ('UPDATE', 'SERVER')),
	ADD CONSTRAINT update_token_quota_key_type_check CHECK ((kind = 'UPDATE') = (key_type_id IS NULL));

CREATE UNIQUE INDEX update_token_quota_uindex
	ON lyna.update_token_quota (account_id, product_id, (coalesce(key_type_id, 0)));

DROP INDEX lyna.update_token_request_pending_uindex;

ALTER TABLE lyna.update_token_request
	DROP CONSTRAINT IF EXISTS update_token_request_kind_check,
	ADD COLUMN key_type_id INTEGER NULL
		REFERENCES lyna.product_key_type (id) ON DELETE CASCADE;

UPDATE lyna.update_token_request r
SET kind = 'SERVER', key_type_id = k.id
FROM lyna.product_key_type k
WHERE r.kind = 'DASHBOARD' AND k.product_id = r.product_id AND k.slug = 'dashboard';

ALTER TABLE lyna.update_token_request
	ADD CONSTRAINT update_token_request_kind_check CHECK (kind IN ('UPDATE', 'SERVER')),
	ADD CONSTRAINT update_token_request_key_type_check CHECK ((kind = 'UPDATE') = (key_type_id IS NULL));

CREATE UNIQUE INDEX update_token_request_pending_uindex
	ON lyna.update_token_request (account_id, product_id, (coalesce(key_type_id, 0)))
	WHERE status = 'PENDING';

ALTER TABLE lyna.product
	DROP COLUMN dashboard_keys,
	DROP COLUMN dashboard_refresh_days,
	DROP COLUMN dashboard_expiry_days,
	DROP COLUMN dashboard_allowance;
