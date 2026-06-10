-- Enable read-only trigger browsing for MySQL family and SQL Server.
UPDATE odc_version_diff_config SET config_value = 'true'
  WHERE config_key = 'support_trigger'
    AND db_mode IN ('MYSQL', 'OB_MYSQL', 'ODP_SHARDING_OB_MYSQL');

INSERT INTO odc_version_diff_config (config_key, db_mode, config_value, min_version)
  VALUES ('support_trigger', 'SQL_SERVER', 'true', '0')
  ON DUPLICATE KEY UPDATE config_value = 'true';

INSERT INTO odc_version_diff_config (config_key, db_mode, config_value, min_version)
  VALUES
    ('support_trigger_ddl', 'SQL_SERVER', 'false', '0'),
    ('support_trigger_compile', 'SQL_SERVER', 'false', '0'),
    ('support_trigger_alterstatus', 'SQL_SERVER', 'false', '0'),
    ('support_trigger_references', 'SQL_SERVER', 'false', '0')
  ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
