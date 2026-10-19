ALTER TABLE lyna.release_webhook
	ADD COLUMN activity_channel_id BIGINT  NULL,
	ADD COLUMN post_push           BOOLEAN NOT NULL DEFAULT TRUE,
	ADD COLUMN post_pr_opened      BOOLEAN NOT NULL DEFAULT TRUE,
	ADD COLUMN post_pr_merged      BOOLEAN NOT NULL DEFAULT TRUE,
	ADD COLUMN post_issue_opened   BOOLEAN NOT NULL DEFAULT TRUE,
	ADD COLUMN post_issue_closed   BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN lyna.release_webhook.activity_channel_id IS
	'Where pushes, pull requests and issues are posted, apart from the releases; null for nowhere.';
