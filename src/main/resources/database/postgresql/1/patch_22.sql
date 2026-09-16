ALTER TABLE lyna.account
	DROP COLUMN IF EXISTS feel;

ALTER TABLE lyna.instance_settings
	DROP COLUMN IF EXISTS default_feel,
	DROP COLUMN IF EXISTS lock_feel,
	DROP COLUMN IF EXISTS allow_user_feel;
