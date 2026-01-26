-- Test table with various SQL Server data types
-- SQL Server specific: int, bigint, smallint, tinyint don't have scale
-- decimal, numeric have both precision and scale
-- varchar, char only have precision (length)
-- datetime2, time only have scale (fractional seconds)

CREATE TABLE dbo.test_data_type (
    col1 INT NOT NULL,
    col2 BIGINT,
    col3 SMALLINT,
    col4 TINYINT,
    col5 DECIMAL(10, 2),
    col6 NUMERIC(18, 4),
    col7 VARCHAR(50),
    col8 CHAR(10),
    col9 NVARCHAR(100),
    col10 NCHAR(20),
    col11 TEXT,
    col12 NTEXT,
    col13 DATETIME,
    col14 DATETIME2(7),
    col15 DATE,
    col16 TIME(7),
    col17 SMALLDATETIME,
    col18 FLOAT,
    col19 REAL,
    col20 MONEY,
    col21 SMALLMONEY,
    col22 BINARY(16),
    col23 VARBINARY(MAX),
    col24 IMAGE,
    col25 BIT,
    col26 UNIQUEIDENTIFIER,
    col27 XML,
    col28 TIMESTAMP
);
GO

-- Test table for column attributes (nullable, default, identity, etc.)
CREATE TABLE dbo.test_other_than_data_type (
    col1 INT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    col2 VARCHAR(50) NULL DEFAULT 'default_value',
    col3 INT NOT NULL DEFAULT 0,
    col4 DATETIME2 DEFAULT GETDATE(),
    col5 VARCHAR(100) NULL,
    col6 INT CHECK (col6 > 0),
    col7 VARCHAR(50) COLLATE SQL_Latin1_General_CP1_CI_AS
);
GO
