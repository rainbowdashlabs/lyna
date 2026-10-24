ALTER TABLE lyna.license
	DROP CONSTRAINT license_source_known;

ALTER TABLE lyna.license
	ADD CONSTRAINT license_source_known
		CHECK (source IN ('KOFI', 'MAIL', 'MANUAL', 'BUILTBYBIT'));

CREATE TABLE lyna.builtbybit_product (
	resource_id BIGINT  NOT NULL,
	addon_id    BIGINT  NOT NULL DEFAULT 0,
	product_id  INTEGER NOT NULL UNIQUE
		REFERENCES lyna.product (id) ON DELETE CASCADE,
	PRIMARY KEY (resource_id, addon_id)
);

COMMENT ON TABLE lyna.builtbybit_product IS
	'The BuiltByBit resource a product is sold as. An addon id of 0 is the resource itself; any other names one of its add-ons.';

CREATE TABLE lyna.builtbybit_purchase_log (
	id          SERIAL    NOT NULL PRIMARY KEY,
	received    TIMESTAMP NOT NULL DEFAULT now(),
	kind        TEXT      NOT NULL
		CHECK (kind IN ('PURCHASE', 'KEY')),
	member_id   BIGINT,
	resource_id BIGINT,
	addon_id    BIGINT,
	product_id  INTEGER
		REFERENCES lyna.product (id) ON DELETE SET NULL,
	outcome     TEXT      NOT NULL,
	raw         TEXT      NOT NULL
);

CREATE INDEX builtbybit_purchase_log_received_index
	ON lyna.builtbybit_purchase_log (received DESC);

COMMENT ON TABLE lyna.builtbybit_purchase_log IS
	'Every call BuiltByBit made: purchase webhooks and licence key requests, as received, with what Lyna did about them. Neither call is signed, so this is the audit trail.';
