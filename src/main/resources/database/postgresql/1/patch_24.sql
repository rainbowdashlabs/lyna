CREATE TABLE lyna.account_email (
	account_id  INTEGER   NOT NULL
		REFERENCES lyna.account (id) ON DELETE CASCADE,
	email       TEXT      NOT NULL,
	verified_at TIMESTAMP NULL,
	added_at    TIMESTAMP NOT NULL DEFAULT now(),
	is_primary  BOOLEAN   NOT NULL DEFAULT FALSE,
	CONSTRAINT account_email_pk PRIMARY KEY (account_id, email)
);

-- An address belongs to one account, once it is proved or once it is the address an account is
-- written to. Both are things somebody can be found by, so both have to name one person.
--
-- A merely claimed address is deliberately not exclusive: reserving one on the strength of somebody
-- typing it would let anyone take an address they do not own away from whoever does.
CREATE UNIQUE INDEX account_email_unique
	ON lyna.account_email (LOWER(email))
	WHERE verified_at IS NOT NULL OR is_primary;

-- The one the application sends to.
CREATE UNIQUE INDEX account_email_one_primary
	ON lyna.account_email (account_id)
	WHERE is_primary;

CREATE INDEX account_email_account_id_index
	ON lyna.account_email (account_id);

INSERT INTO lyna.account_email (account_id, email, verified_at, is_primary)
SELECT id, email, CASE WHEN email_verified THEN now() END, TRUE
FROM lyna.account
WHERE email IS NOT NULL;

ALTER TABLE lyna.account
	DROP COLUMN email,
	DROP COLUMN email_verified;
