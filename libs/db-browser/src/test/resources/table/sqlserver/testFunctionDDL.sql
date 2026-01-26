-- Test user-defined functions

-- Scalar function
CREATE FUNCTION dbo.function_test (@param1 INT, @param2 INT)
RETURNS INT
AS
BEGIN
    DECLARE @result INT;
    SET @result = @param1 + @param2;
    RETURN @result;
END;
GO

-- Table-valued function
CREATE FUNCTION dbo.function_table_valued (@param1 INT)
RETURNS TABLE
AS
RETURN
(
    SELECT col1, col2
    FROM dbo.test_data_type
    WHERE col1 = @param1
);
GO

-- Function without parameters
CREATE FUNCTION dbo.function_no_params()
RETURNS VARCHAR(50)
AS
BEGIN
    RETURN 'Hello World';
END;
GO
