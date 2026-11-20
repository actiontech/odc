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
package com.oceanbase.odc.service.dml.converter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * Quote ClickHouse string-family literals (S4 §10.5). MySQL converters only know varchar/text, so
 * raw {@code String} / {@code Nullable(String)} would otherwise be emitted unquoted.
 */
public class ClickHouseStringConverter extends BaseStringConverter {

    private static final Pattern WRAPPER =
            Pattern.compile("(?i)^(Nullable|LowCardinality)\\((.+)\\)$");
    private static final Set<String> STRING_TYPES = new HashSet<>(Arrays.asList(
            "string", "fixedstring", "uuid", "ipv4", "ipv6", "enum8", "enum16",
            "nchar", "varchar", "char", "nvarchar2", "varchar2",
            "tinytext", "mediumtext", "longtext", "text"));

    @Override
    protected DialectType dialectType() {
        return DialectType.OB_MYSQL;
    }

    @Override
    protected Collection<String> getSupportDataTypeNames() {
        return STRING_TYPES;
    }

    @Override
    protected boolean supports(@NonNull DataType dataType) {
        String name = unwrap(dataType.getDataTypeName());
        if (name == null) {
            return false;
        }
        int paren = name.indexOf('(');
        if (paren > 0) {
            name = name.substring(0, paren);
        }
        return STRING_TYPES.contains(name.toLowerCase(Locale.ROOT));
    }

    private static String unwrap(String typeName) {
        if (typeName == null) {
            return null;
        }
        String current = typeName.trim();
        for (int i = 0; i < 3; i++) {
            Matcher m = WRAPPER.matcher(current);
            if (!m.matches()) {
                break;
            }
            current = m.group(2).trim();
        }
        return current;
    }

}
