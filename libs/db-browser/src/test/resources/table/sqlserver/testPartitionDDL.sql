-- Test table with partition
-- SQL Server supports RANGE partitioning

CREATE TABLE dbo.test_partition (
    id INT NOT NULL,
    col1 INT NOT NULL,
    col2 VARCHAR(50),
    col3 DATETIME2
) ON [PRIMARY];
GO

-- Note: SQL Server partitioning requires partition function and partition scheme
-- This is a simplified example. In real scenarios, you would need:
-- CREATE PARTITION FUNCTION ...
-- CREATE PARTITION SCHEME ...
-- Then create table with ON partition_scheme_name (partition_column)

-- For testing purposes, we'll create a simple partitioned table
-- SQL Server 2016+ supports inline partition specification

IF EXISTS (SELECT * FROM sys.partition_functions WHERE name = 'pf_test_partition')
    DROP PARTITION FUNCTION pf_test_partition;
GO

IF EXISTS (SELECT * FROM sys.partition_schemes WHERE name = 'ps_test_partition')
    DROP PARTITION SCHEME ps_test_partition;
GO

-- Create partition function
CREATE PARTITION FUNCTION pf_test_partition (INT)
AS RANGE LEFT FOR VALUES (10, 20, 30);
GO

-- Create partition scheme
CREATE PARTITION SCHEME ps_test_partition
AS PARTITION pf_test_partition
TO ([PRIMARY], [PRIMARY], [PRIMARY], [PRIMARY]);
GO

-- Drop and recreate table with partition
IF OBJECT_ID('dbo.test_partition', 'U') IS NOT NULL
    DROP TABLE dbo.test_partition;
GO

CREATE TABLE dbo.test_partition (
    id INT NOT NULL,
    col1 INT NOT NULL,
    col2 VARCHAR(50),
    col3 DATETIME2
) ON ps_test_partition (col1);
GO
