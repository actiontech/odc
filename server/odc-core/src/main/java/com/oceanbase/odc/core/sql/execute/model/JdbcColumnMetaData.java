/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.core.sql.execute.model;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.shared.constant.OdcConstants;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author wenniu.ly
 * @date 2021/8/30
 */
@Slf4j
@Data
@NoArgsConstructor
public class JdbcColumnMetaData {
    private boolean autoIncrement;
    private boolean caseSensitive;
    private boolean searchable;
    private boolean currency;
    /**
     * <pre>
     * 0 indicates that a column does not allow <code>NULL</code> values.
     * 1 indicates that a column allows <code>NULL</code> values.
     * 2 indicates that the nullability of a column's values is unknown.
     * </pre>
     */
    private int nullable;
    private boolean signed;
    private int columnDisplaySize;
    private String columnLabel;
    private String columnName;
    private String schemaName;
    private int precision;
    private int scale;
    private String tableName;
    private String catalogName;
    /**
     * Indicates the designated column's SQL type.
     *
     * @return SQL type from java.sql.Types
     * @see Types
     */
    private int columnType;
    private String columnTypeName;
    private boolean readOnly;
    private boolean writable;
    private boolean definitelyWritable;
    private String columnClassName;

    /**
     * below ODC additional meta
     */
    private String columnComment;

    /**
     * mark ODC internal column for special cases, <br>
     * sql console result-set viewer may hide these internal rows
     */
    private boolean internal = false;

    /**
     * indicate whether this column can be editable in result set
     */
    private boolean editable = false;

    /**
     * indicate whether this column is masked
     */
    private boolean masked = false;

    public JdbcColumnMetaData(ResultSetMetaData resultSetMetaData, int index) throws SQLException {
        this.autoIncrement = resultSetMetaData.isAutoIncrement(index);
        this.caseSensitive = resultSetMetaData.isCaseSensitive(index);
        this.searchable = getBooleanSafely(resultSetMetaData, index, "isSearchable",
                () -> resultSetMetaData.isSearchable(index));
        this.currency = resultSetMetaData.isCurrency(index);
        this.nullable = resultSetMetaData.isNullable(index);
        this.signed = getBooleanSafely(resultSetMetaData, index, "isSigned",
                () -> resultSetMetaData.isSigned(index));
        this.columnDisplaySize = resultSetMetaData.getColumnDisplaySize(index);
        this.columnLabel = resultSetMetaData.getColumnLabel(index);
        this.columnName = resultSetMetaData.getColumnName(index);
        this.schemaName = getStringSafely(resultSetMetaData, index, "getSchemaName",
                () -> resultSetMetaData.getSchemaName(index));
        this.precision = resultSetMetaData.getPrecision(index);
        this.scale = resultSetMetaData.getScale(index);
        this.tableName = getStringSafely(resultSetMetaData, index, "getTableName",
                () -> resultSetMetaData.getTableName(index));
        this.catalogName = getStringSafely(resultSetMetaData, index, "getCatalogName",
                () -> resultSetMetaData.getCatalogName(index));
        this.columnType = resultSetMetaData.getColumnType(index);
        this.columnTypeName = resultSetMetaData.getColumnTypeName(index);
        this.readOnly = getBooleanSafely(resultSetMetaData, index, "isReadOnly",
                () -> resultSetMetaData.isReadOnly(index));
        this.writable = getBooleanSafely(resultSetMetaData, index, "isWritable",
                () -> resultSetMetaData.isWritable(index));
        this.definitelyWritable = getBooleanSafely(resultSetMetaData, index, "isDefinitelyWritable",
                () -> resultSetMetaData.isDefinitelyWritable(index));
        this.columnClassName = resultSetMetaData.getColumnClassName(index);

        if (StringUtils.equals(OdcConstants.ODC_INTERNAL_ROWID, this.columnName)) {
            this.internal = true;
        }
    }

    private static String getStringSafely(ResultSetMetaData metaData, int index, String methodName,
            SqlStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (SQLFeatureNotSupportedException e) {
            log.debug("ResultSetMetaData.{}({}) not supported by JDBC driver [{}], returning empty string",
                    methodName, index, metaData.getClass().getName());
            return "";
        } catch (SQLException e) {
            if ("Method not supported".equals(e.getMessage())) {
                log.debug("ResultSetMetaData.{}({}) not supported by JDBC driver [{}], returning empty string",
                        methodName, index, metaData.getClass().getName());
                return "";
            }
            throw new RuntimeException(e);
        }
    }

    private static boolean getBooleanSafely(ResultSetMetaData metaData, int index, String methodName,
            SqlBooleanSupplier supplier) {
        try {
            return supplier.get();
        } catch (SQLFeatureNotSupportedException e) {
            log.debug("ResultSetMetaData.{}({}) not supported by JDBC driver [{}], returning false",
                    methodName, index, metaData.getClass().getName());
            return false;
        } catch (SQLException e) {
            if ("Method not supported".equals(e.getMessage())) {
                log.debug("ResultSetMetaData.{}({}) not supported by JDBC driver [{}], returning false",
                        methodName, index, metaData.getClass().getName());
                return false;
            }
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SqlStringSupplier {
        String get() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlBooleanSupplier {
        boolean get() throws SQLException;
    }

    public String schemaName() {
        if (StringUtils.isNotBlank(this.schemaName)) {
            return this.schemaName;
        }
        return this.catalogName;
    }
}
