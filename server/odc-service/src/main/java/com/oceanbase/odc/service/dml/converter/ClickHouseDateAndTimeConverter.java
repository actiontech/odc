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

import com.oceanbase.odc.service.dml.DataValue;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * Quote ClickHouse Date / DateTime / DateTime64 literals (S4 §10.5), including Nullable wrappers.
 */
public class ClickHouseDateAndTimeConverter extends BaseDataConverter {

    private static final Pattern WRAPPER =
            Pattern.compile("(?i)^(Nullable|LowCardinality)\\((.+)\\)$");
    private static final Set<String> DATE_TYPES = new HashSet<>(Arrays.asList(
            "date", "date32", "datetime", "datetime64", "time", "timestamp", "year"));

    @Override
    protected Collection<String> getSupportDataTypeNames() {
        return DATE_TYPES;
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
        return DATE_TYPES.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    protected String doConvert(@NonNull DataValue value) {
        return "'" + value.getValue() + "'";
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
