-- Test stored procedures

CREATE PROCEDURE dbo.procedure_test
    @param1 INT,
    @param2 VARCHAR(50) = 'default'
AS
BEGIN
    SET NOCOUNT ON;
    SELECT @param1 AS param1, @param2 AS param2;
END;
GO

-- Procedure without parameters
CREATE PROCEDURE dbo.procedure_without_parameters
AS
BEGIN
    SET NOCOUNT ON;
    SELECT GETDATE() AS current_date;
END;
GO

-- Procedure with output parameter
CREATE PROCEDURE dbo.procedure_with_output
    @input_param INT,
    @output_param INT OUTPUT
AS
BEGIN
    SET @output_param = @input_param * 2;
END;
GO
