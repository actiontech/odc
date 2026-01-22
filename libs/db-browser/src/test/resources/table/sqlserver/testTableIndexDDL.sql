-- Test table for index types
-- SQL Server supports CLUSTERED and NONCLUSTERED indexes

CREATE TABLE dbo.test_index_type (
    id INT NOT NULL,
    col1 VARCHAR(50),
    col2 INT,
    col3 DATETIME2
);
GO

-- Create clustered index
CREATE CLUSTERED INDEX idx_clustered ON dbo.test_index_type (id);
GO

-- Create nonclustered index
CREATE NONCLUSTERED INDEX idx_nonclustered ON dbo.test_index_type (col1, col2);
GO

-- Create unique nonclustered index
CREATE UNIQUE NONCLUSTERED INDEX idx_unique ON dbo.test_index_type (col3);
GO

-- Test table for index range (not applicable in SQL Server, but for compatibility)
CREATE TABLE dbo.test_index_range (
    id INT NOT NULL PRIMARY KEY,
    col1 VARCHAR(50)
);
GO

CREATE NONCLUSTERED INDEX idx_test_range ON dbo.test_index_range (col1);
GO
