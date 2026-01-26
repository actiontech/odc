-- Test synonyms

-- Create synonym for a table
CREATE SYNONYM dbo.synonym_test FOR dbo.test_data_type;
GO

-- Synonym for a view
CREATE SYNONYM dbo.synonym_view FOR dbo.view_test1;
GO

-- Synonym for a procedure
CREATE SYNONYM dbo.synonym_proc FOR dbo.procedure_test;
GO
