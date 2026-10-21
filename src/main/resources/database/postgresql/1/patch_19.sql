ALTER TABLE lyna.debug_report_entry
	DROP CONSTRAINT debug_report_entry_kind_check,
	ADD CONSTRAINT debug_report_entry_kind_check
		CHECK (kind IN ('LOG', 'PLUGIN_LOG', 'INTERNAL_EXCEPTION', 'EXTERNAL_EXCEPTION', 'CONFIG', 'META', 'CHECK', 'CHART')),
	ADD COLUMN chart JSONB,
	ADD CONSTRAINT debug_report_entry_check_chart
		CHECK ((kind = 'CHART') = (chart IS NOT NULL));

COMMENT ON COLUMN lyna.debug_report_entry.chart IS
	'Type, data and hints of a CHART section, as the plugin sent them; set exactly on CHART sections.';
