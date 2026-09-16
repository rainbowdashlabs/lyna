CREATE TABLE lyna.demo_artifact (
	kind       TEXT      NOT NULL,
	artifact   TEXT      NOT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT now(),
	CONSTRAINT demo_artifact_pk PRIMARY KEY (kind, artifact)
);
