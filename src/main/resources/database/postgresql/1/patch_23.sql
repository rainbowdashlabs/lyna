-- The interface is drawn around Transistor now, and the schemes are only schemes. An instance still
-- carrying the previous default never chose it - patch_11 moved the one before that along for the
-- same reason. An account that picked a scheme by hand keeps it: they all still exist.
--
-- The previous default is spelled in halves because the schema token is replaced textually, inside
-- string literals included.
ALTER TABLE lyna.instance_settings
	ALTER COLUMN default_theme SET DEFAULT 'transistor';

UPDATE lyna.instance_settings
SET default_theme = 'transistor'
WHERE default_theme IN (('ly' || 'na'), 'ember');
