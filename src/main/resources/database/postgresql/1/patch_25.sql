-- Where a licence came from, which used to be a prefix on the identifier of whoever bought it.
ALTER TABLE lyna.license
	ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'MANUAL';

UPDATE lyna.license
SET source          = 'KOFI',
    user_identifier = substring(user_identifier FROM 6)
WHERE user_identifier LIKE 'kofi:%';

ALTER TABLE lyna.license
	ADD CONSTRAINT license_source_known
		CHECK (source IN ('KOFI', 'MAIL', 'MANUAL'));

CREATE INDEX license_source_identifier_index
	ON lyna.license (source, LOWER(user_identifier));
