ALTER TABLE lyna.account
	ADD COLUMN IF NOT EXISTS username      TEXT NULL,
	ADD COLUMN IF NOT EXISTS discriminator TEXT NULL;

ALTER TABLE lyna.account
	ADD CONSTRAINT account_discriminator_is_four_digits
		CHECK (discriminator IS NULL OR discriminator ~ '^[0-9]{4}$');

UPDATE lyna.account a
SET username = i.handle
FROM lyna.account_identity i
WHERE i.account_id = a.id
  AND i.provider = 'discord'
  AND i.handle IS NOT NULL
  AND a.username IS NULL;

CREATE UNIQUE INDEX account_username_unique
	ON lyna.account (lower(username), COALESCE(discriminator, ''))
	WHERE username IS NOT NULL;
