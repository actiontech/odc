/*
 * Copyright (c) 2025 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

SET @idx_table := 'iam_user';
SET @idx_name := 'idx_iam_user_org_id_creator_id';
SET @idx_cols := 'organization_id, creator_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_table := 'iam_role';
SET @idx_name := 'idx_iam_role_org_id_creator_id';
SET @idx_cols := 'organization_id, creator_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_table := 'connect_connection';
SET @idx_name := 'idx_connect_connection_org_id_creator_id';
SET @idx_cols := 'organization_id, creator_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_table := 'task_task';
SET @idx_name := 'idx_task_task_connection_id_organization_id';
SET @idx_cols := 'connection_id, organization_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_table := 'schedule_schedule';
SET @idx_name := 'idx_schedule_schedule_connection_id_organization_id';
SET @idx_cols := 'connection_id, organization_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_table := 'data_security_sensitive_rule';
SET @idx_name := 'idx_data_security_sensitive_rule_organization_id';
SET @idx_cols := 'organization_id';
SET @ddl := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = @idx_table
     AND index_name = @idx_name) = 0,
  CONCAT('CREATE INDEX ', @idx_name, ' ON ', @idx_table, '(', @idx_cols, ')'),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
