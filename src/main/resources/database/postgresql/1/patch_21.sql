CREATE TABLE lyna.github_release_sync (
	owner      TEXT        NOT NULL,
	repository TEXT        NOT NULL,
	fetched_at TIMESTAMPTZ NOT NULL,
	etag       TEXT,
	CONSTRAINT github_release_sync_pk
		PRIMARY KEY (owner, repository)
);

COMMENT ON TABLE lyna.github_release_sync IS
	'When the releases of a GitHub repository were last read from GitHub, and the ETag it answered with. Owner and repository are stored in lower case.';

CREATE TABLE lyna.github_release (
	owner        TEXT        NOT NULL,
	repository   TEXT        NOT NULL,
	release_id   BIGINT      NOT NULL,
	tag          TEXT        NOT NULL,
	name         TEXT,
	body         TEXT,
	html_url     TEXT        NOT NULL,
	prerelease   BOOLEAN     NOT NULL,
	published_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT github_release_pk
		PRIMARY KEY (owner, repository, release_id),
	CONSTRAINT github_release_sync_fk
		FOREIGN KEY (owner, repository) REFERENCES lyna.github_release_sync (owner, repository)
			ON DELETE CASCADE
);

CREATE INDEX github_release_published_index
	ON lyna.github_release (owner, repository, published_at DESC);

COMMENT ON TABLE lyna.github_release IS
	'The published releases of a GitHub repository, as product pages show their release notes. Read lazily from GitHub and written by the release webhook; drafts are never stored.';
