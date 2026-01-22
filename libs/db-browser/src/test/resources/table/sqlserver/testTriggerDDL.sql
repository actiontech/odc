-- Test triggers

-- AFTER INSERT trigger
CREATE TRIGGER dbo.trigger_test
ON dbo.test_data_type
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    -- Trigger logic here
    PRINT 'Row inserted into test_data_type';
END;
GO

-- INSTEAD OF trigger
CREATE TRIGGER dbo.trigger_instead_of
ON dbo.view_test1
INSTEAD OF INSERT
AS
BEGIN
    SET NOCOUNT ON;
    -- Trigger logic here
    PRINT 'Instead of insert on view_test1';
END;
GO
