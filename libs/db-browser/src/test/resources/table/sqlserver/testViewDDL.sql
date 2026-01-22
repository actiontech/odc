-- Test views

CREATE VIEW dbo.view_test1
AS
SELECT 
    col1,
    col2
FROM dbo.test_data_type
WHERE col1 IS NOT NULL;
GO

CREATE VIEW dbo.view_test2
AS
SELECT 
    id,
    col1
FROM dbo.test_other_than_data_type;
GO

-- View with WITH CHECK OPTION (SQL Server supports this)
CREATE VIEW dbo.view_test3
AS
SELECT 
    id,
    col1
FROM dbo.test_other_than_data_type
WHERE col1 IS NOT NULL
WITH CHECK OPTION;
GO
