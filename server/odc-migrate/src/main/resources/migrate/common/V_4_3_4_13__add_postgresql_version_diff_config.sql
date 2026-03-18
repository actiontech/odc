-- Add PostgreSQL version diff config for supporting view/function/procedure and other features
-- This fixes the issue where PostgreSQL data source doesn't show view/function/procedure groups in resource tree

-- Support view/function/procedure for PostgreSQL
-- PostgreSQL has supported views since early versions, functions since early versions
-- CREATE PROCEDURE is supported since PostgreSQL 11
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_view','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_function','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_procedure','POSTGRESQL','true','11',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Column data types for PostgreSQL
-- Reference: https://www.postgresql.org/docs/current/datatype.html
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('column_data_type', 'POSTGRESQL',
'smallint:NUMERIC, integer:NUMERIC, bigint:NUMERIC, decimal:NUMERIC, numeric:NUMERIC, real:NUMERIC, double precision:NUMERIC, serial:NUMERIC, bigserial:NUMERIC, smallserial:NUMERIC, money:NUMERIC, 
char:TEXT, varchar:TEXT, text:OBJECT, bytea:OBJECT, 
timestamp:TIMESTAMP, timestamptz:TIMESTAMP, date:DATE, time:TIME, timetz:TIME, interval:OBJECT, 
boolean:OBJECT, bool:OBJECT,
json:OBJECT, jsonb:OBJECT, 
uuid:OBJECT, xml:OBJECT,
inet:OBJECT, cidr:OBJECT, macaddr:OBJECT, macaddr8:OBJECT,
point:OBJECT, line:OBJECT, lseg:OBJECT, box:OBJECT, path:OBJECT, polygon:OBJECT, circle:OBJECT', 
'0', CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Support other PostgreSQL features
-- PostgreSQL supports constraints, partitions (declarative partitioning since PG 10), foreign keys
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_constraint','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_constraint_modify','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_partition','POSTGRESQL','true','10',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_partition_modify','POSTGRESQL','true','10',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_show_foreign_key','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL supports kill session/query via pg_cancel_backend/pg_terminate_backend
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_kill_session','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_kill_query','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL supports EXPLAIN for execution plans
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_sql_explain','POSTGRESQL','true','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Features NOT supported by PostgreSQL (set to false)
-- PostgreSQL doesn't have built-in recycle bin
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_recycle_bin','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL doesn't have auto-increment like MySQL, it uses SERIAL/IDENTITY
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_partition_plan','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL doesn't have packages like Oracle
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_package','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL doesn't have rowid like Oracle
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_rowid','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL supports sequences, but ODC doesn't implement SequenceExtensionPoint yet
-- Keep these features disabled until plugin extension is implemented (same as SQL Server)
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_sequence','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL supports triggers, but ODC doesn't implement TriggerExtensionPoint yet
-- Keep these features disabled until plugin extension is implemented (same as SQL Server)
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_trigger','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_trigger_ddl','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL supports custom types, but ODC doesn't implement TypeExtensionPoint yet
-- Keep these features disabled until plugin extension is implemented (same as SQL Server)
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_type','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PostgreSQL doesn't have synonyms like Oracle
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_synonym','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Mock data support - needs evaluation with PostgreSQL data types
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_mock_data','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Shadow table - not supported for PostgreSQL
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_shadowtable','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- PL/SQL debug - not supported for PostgreSQL
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_pl_debug','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- SQL trace - PostgreSQL uses EXPLAIN ANALYZE instead
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_sql_trace','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

-- Data export/import
insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_data_export','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_db_import','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;

insert into `odc_version_diff_config`(`config_key`,`db_mode`,`config_value`,`min_version`,`gmt_create`) 
values('support_db_export','POSTGRESQL','false','0',CURRENT_TIMESTAMP) 
ON DUPLICATE KEY update `config_key`=`config_key`;
