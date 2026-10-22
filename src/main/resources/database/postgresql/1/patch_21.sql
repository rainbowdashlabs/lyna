CREATE TABLE lyna.version_manifest (
	repository  TEXT  NOT NULL,
	group_id    TEXT  NOT NULL,
	artifact_id TEXT  NOT NULL,
	version     TEXT  NOT NULL,
	sha1        TEXT  NOT NULL,
	manifest    JSONB NOT NULL,
	CONSTRAINT version_manifest_pk
		PRIMARY KEY (repository, group_id, artifact_id, version)
);

COMMENT ON TABLE lyna.version_manifest IS
	'The plugin manifest published beside a build in Nexus, with the classifier manifest. Kept by Maven coordinates and replaced when its sha1 changes, which only a redeployed snapshot does.';
