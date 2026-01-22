-- Test tables for constraints (Primary Key, Foreign Key, Unique, Check)

-- Parent table with Primary Key
CREATE TABLE dbo.test_fk_parent (
    id INT NOT NULL,
    col1 VARCHAR(50) NOT NULL,
    col2 INT,
    CONSTRAINT pk_test_parent PRIMARY KEY (id, col1)
);
GO

-- Child table with Foreign Key
CREATE TABLE dbo.test_fk_child (
    id INT NOT NULL PRIMARY KEY,
    parent_id INT NOT NULL,
    parent_col1 VARCHAR(50) NOT NULL,
    col1 VARCHAR(50),
    CONSTRAINT fk_test_child FOREIGN KEY (parent_id, parent_col1) 
        REFERENCES dbo.test_fk_parent (id, col1)
        ON DELETE CASCADE
        ON UPDATE NO ACTION
);
GO

-- Table with Unique constraint
CREATE TABLE dbo.test_unique_constraint (
    id INT NOT NULL PRIMARY KEY,
    col1 VARCHAR(50) UNIQUE,
    col2 INT,
    CONSTRAINT uq_test_col2 UNIQUE (col2)
);
GO

-- Table with Check constraint
CREATE TABLE dbo.test_check_constraint (
    id INT NOT NULL PRIMARY KEY,
    col1 INT CHECK (col1 > 0),
    col2 VARCHAR(50) CHECK (LEN(col2) > 5)
);
GO
