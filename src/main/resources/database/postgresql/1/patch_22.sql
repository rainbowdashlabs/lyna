ALTER TABLE lyna.update_token
	ADD COLUMN kind         TEXT        NOT NULL DEFAULT 'UPDATE' CHECK (kind IN ('UPDATE', 'DASHBOARD')),
	ADD COLUMN rotated_at   TIMESTAMPTZ NULL,
	ADD COLUMN refreshed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN lyna.update_token.kind IS
	'UPDATE for an upgrade token an updater trades for a download, DASHBOARD for a key a server proves itself to the hosted dashboard with.';
COMMENT ON COLUMN lyna.update_token.rotated_at IS
	'When the current token was issued: on creation, on a trade and on a reissue. A dashboard key is traded for its successor once this is older than the product''s refresh interval.';
COMMENT ON COLUMN lyna.update_token.refreshed_at IS
	'When a server last asked to refresh its dashboard key, which is what holds a refreshing loop off. A verification by the gateway does not count.';

ALTER TABLE lyna.update_token_quota
	ADD COLUMN kind TEXT NOT NULL DEFAULT 'UPDATE' CHECK (kind IN ('UPDATE', 'DASHBOARD')),
	DROP CONSTRAINT update_token_quota_pk,
	ADD CONSTRAINT update_token_quota_pk PRIMARY KEY (account_id, product_id, kind);

ALTER TABLE lyna.update_token_request
	ADD COLUMN kind TEXT NOT NULL DEFAULT 'UPDATE' CHECK (kind IN ('UPDATE', 'DASHBOARD'));

DROP INDEX lyna.update_token_request_pending_uindex;

CREATE UNIQUE INDEX update_token_request_pending_uindex
	ON lyna.update_token_request (account_id, product_id, kind)
	WHERE status = 'PENDING';

ALTER TABLE lyna.product
	ADD COLUMN dashboard_keys         BOOLEAN NOT NULL DEFAULT FALSE,
	ADD COLUMN dashboard_refresh_days INTEGER NOT NULL DEFAULT 7 CHECK (dashboard_refresh_days > 0),
	ADD COLUMN dashboard_expiry_days  INTEGER NOT NULL DEFAULT 30 CHECK (dashboard_expiry_days > 0),
	ADD COLUMN dashboard_allowance    INTEGER NOT NULL DEFAULT 10 CHECK (dashboard_allowance >= 0);

COMMENT ON COLUMN lyna.product.dashboard_keys IS
	'Whether accounts may hold dashboard keys for this product. Switching it off refuses every key at its next refresh or verification.';
COMMENT ON COLUMN lyna.product.dashboard_refresh_days IS
	'How old a dashboard key may get before a refresh trades it for its successor.';
COMMENT ON COLUMN lyna.product.dashboard_expiry_days IS
	'How long a dashboard key may go unused before it expires and has to be reissued.';
COMMENT ON COLUMN lyna.product.dashboard_allowance IS
	'Dashboard keys a buyer may hold before asking for more. An account that only took the trial may hold one.';
