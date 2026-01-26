-- Test sequences (SQL Server 2012+)

CREATE SEQUENCE dbo.sequence_test
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 1000
    CYCLE;
GO

-- Sequence with different options
CREATE SEQUENCE dbo.sequence_test2
    START WITH 100
    INCREMENT BY 5
    MINVALUE 0
    MAXVALUE 10000
    NO CYCLE
    CACHE 10;
GO
