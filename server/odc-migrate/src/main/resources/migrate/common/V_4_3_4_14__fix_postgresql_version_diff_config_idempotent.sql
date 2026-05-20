-- Fix V_4_3_4_13 idempotency bug:
-- early versions of V_4_3_4_13 inserted support_trigger/support_sequence/support_type with
-- config_value='true', and the trailing `ON DUPLICATE KEY UPDATE config_key=config_key`
-- (self-assignment) prevents the corrected SQL ('false') from overwriting existing rows.
-- This migration explicitly UPDATEs only the three PG-specific config keys whose value is still 'true'.
-- Refs: dms-ee#850, compat-RISK-1

UPDATE `odc_version_diff_config`
   SET `config_value` = 'false'
 WHERE `db_mode` = 'POSTGRESQL'
   AND `config_key` IN ('support_trigger', 'support_sequence', 'support_type')
   AND `config_value` = 'true';
