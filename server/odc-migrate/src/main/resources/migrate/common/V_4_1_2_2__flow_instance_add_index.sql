-- Add index for table `flow_instance`
SET @idx_table := 'flow_instance';
SET @idx_name := 'flow_instance_idx_organization_id_creator_id';
SET @idx_cols := 'organization_id,creator_id';
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