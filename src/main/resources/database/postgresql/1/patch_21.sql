CREATE TABLE lyna.license_invite (
	license_id INTEGER   NOT NULL
		REFERENCES lyna.license (id) ON DELETE CASCADE,
	email      TEXT      NOT NULL,
	invited_at TIMESTAMP NOT NULL DEFAULT now(),
	expires_at TIMESTAMP NOT NULL,
	CONSTRAINT license_invite_pk PRIMARY KEY (license_id, email)
);

CREATE INDEX license_invite_email_index
	ON lyna.license_invite (lower(email));
