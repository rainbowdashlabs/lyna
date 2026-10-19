ALTER TABLE lyna.account_session
	ADD COLUMN revoked_at TIMESTAMP NULL;

COMMENT ON COLUMN lyna.account_session.revoked_at IS
	'When the session was ended. Its jti stays the same across renewals, so this ends every token it was ever issued.';

UPDATE lyna.account_session s
SET revoked_at = r.revoked_at
FROM lyna.revoked_jti r
WHERE r.jti = s.jti;

DROP TABLE lyna.revoked_jti;
