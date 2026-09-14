ALTER TABLE lyna.instance_settings
	ALTER COLUMN default_theme SET DEFAULT ('ly' || 'na');

UPDATE lyna.instance_settings
SET default_theme = ('ly' || 'na')
WHERE default_theme = 'ember';

UPDATE lyna.account
SET theme = NULL
WHERE theme = 'ember';
