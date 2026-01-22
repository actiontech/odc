-- Drop test objects for SQL Server SchemaAccessor tests
-- Note: Execute in reverse order of creation to handle dependencies

IF OBJECT_ID('dbo.trigger_test', 'TR') IS NOT NULL
    DROP TRIGGER dbo.trigger_test;
GO

IF OBJECT_ID('dbo.synonym_test', 'SN') IS NOT NULL
    DROP SYNONYM dbo.synonym_test;
GO

IF OBJECT_ID('dbo.sequence_test', 'SO') IS NOT NULL
    DROP SEQUENCE dbo.sequence_test;
GO

IF OBJECT_ID('dbo.function_test', 'FN') IS NOT NULL
    DROP FUNCTION dbo.function_test;
GO

IF OBJECT_ID('dbo.procedure_test', 'P') IS NOT NULL
    DROP PROCEDURE dbo.procedure_test;
GO

IF OBJECT_ID('dbo.view_test1', 'V') IS NOT NULL
    DROP VIEW dbo.view_test1;
GO

IF OBJECT_ID('dbo.view_test2', 'V') IS NOT NULL
    DROP VIEW dbo.view_test2;
GO

IF OBJECT_ID('dbo.test_fk_child', 'U') IS NOT NULL
    DROP TABLE dbo.test_fk_child;
GO

IF OBJECT_ID('dbo.test_fk_parent', 'U') IS NOT NULL
    DROP TABLE dbo.test_fk_parent;
GO

IF OBJECT_ID('dbo.test_index_type', 'U') IS NOT NULL
    DROP TABLE dbo.test_index_type;
GO

IF OBJECT_ID('dbo.test_other_than_data_type', 'U') IS NOT NULL
    DROP TABLE dbo.test_other_than_data_type;
GO

IF OBJECT_ID('dbo.test_data_type', 'U') IS NOT NULL
    DROP TABLE dbo.test_data_type;
GO

IF OBJECT_ID('dbo.test_partition', 'U') IS NOT NULL
    DROP TABLE dbo.test_partition;
GO
