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
package com.oceanbase.odc.service.dml;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.service.dml.model.DataModifyUnit;

public class MongoUpdateGeneratorTest {

    @Test
    public void generate_fieldUpdate_usesSetNull() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit score = unit("score", "1", null);
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, score));
        Assert.assertTrue(sql.contains("updateOne"));
        Assert.assertTrue(sql.contains("$set"));
        Assert.assertTrue(sql.contains("\"score\": null"));
        Assert.assertTrue(sql.trim().endsWith(";"));
        Assert.assertFalse(sql.contains("$unset"));
        Assert.assertFalse(sql.toLowerCase().contains("commit"));
        Assert.assertFalse(sql.contains("replaceOne"));
    }

    @Test
    public void generate_twoFieldChanges_singleUpdateOne() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit name = unit("name", "old", "new");
        DataModifyUnit score = unit("score", "1", "2");
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, name, score));
        Assert.assertTrue(sql.contains("updateOne"));
        Assert.assertTrue(sql.contains("\"name\": \"new\""));
        // AC-HF-004: cell values stay string literals — no number guess-upcast
        Assert.assertTrue(sql.contains("\"score\": \"2\""));
        Assert.assertFalse(sql.contains("replaceOne"));
    }

    @Test
    public void generate_stringHexId_notPromotedToObjectId() {
        DataModifyUnit id = unit("_id", "6afc29c73e5296e2582ea3cf", "6afc29c73e5296e2582ea3cf");
        DataModifyUnit name = unit("name", "old", "new");
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, name));
        Assert.assertTrue(sql.contains("_id: \"6afc29c73e5296e2582ea3cf\""));
        Assert.assertFalse(sql.contains("ObjectId(\"6afc29c73e5296e2582ea3cf\")"));
    }

    @Test
    public void generate_stringPhoneTrueNull_stayQuoted() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit phone = unit("phone", "old", "13800138000");
        DataModifyUnit flag = unit("flag", "old", "true");
        DataModifyUnit nil = unit("nil_field", "old", "null");
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures",
                Arrays.asList(id, phone, flag, nil));
        Assert.assertTrue(sql.contains("\"phone\": \"13800138000\""));
        Assert.assertTrue(sql.contains("\"flag\": \"true\""));
        Assert.assertTrue(sql.contains("\"nil_field\": \"null\""));
        Assert.assertFalse(sql.matches("(?s).*\"flag\":\\s*true(?!\").*"));
        Assert.assertFalse(sql.contains("\"nil_field\": null"));
    }

    @Test
    public void generate_newlineInValue_escapedCompleteUpdateOne() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit note = unit("note", "old", "line1\nline2");
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, note));
        Assert.assertTrue(sql.contains("\"note\": \"line1\\nline2\""));
        Assert.assertFalse(sql.contains("\"note\": \"line1\nline2\""));
        Assert.assertTrue(sql.trim().endsWith(";"));
        Assert.assertEquals(1, sql.split("\n").length);
    }

    @Test(expected = BadRequestException.class)
    public void generate_rawJsonChange_rejected() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit raw = unit("_raw_json",
                "{\"_id\":{\"$oid\":\"507f1f77bcf86cd799439011\"},\"name\":\"old\"}",
                "{\"_id\":{\"$oid\":\"507f1f77bcf86cd799439011\"},\"name\":\"new\",\"extra\":1}");
        MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, raw));
    }

    @Test(expected = BadRequestException.class)
    public void generate_rawJsonWithFieldChange_rejected() {
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit name = unit("name", "old", "new");
        DataModifyUnit raw = unit("_raw_json",
                "{\"_id\":{\"$oid\":\"507f1f77bcf86cd799439011\"},\"name\":\"old\"}",
                "{\"_id\":{\"$oid\":\"507f1f77bcf86cd799439011\"},\"name\":\"new\"}");
        MongoUpdateGenerator.generate("workbench_edit_fixtures", Arrays.asList(id, name, raw));
    }

    /** AC-HF-010: unchanged _raw_json supplies types — number/boolean unquoted; strings stay quoted. */
    @Test
    public void generate_unchangedRawJson_preservesNumberBooleanAndQuotedStrings() {
        String raw = "{\"_id\":{\"$oid\":\"507f1f77bcf86cd799439011\"},"
                + "\"score\":1,\"flag\":true,"
                + "\"phone\":\"13800138000\",\"flag_str\":\"true\",\"null_str\":\"null\"}";
        DataModifyUnit id = unit("_id", "ObjectId(\"507f1f77bcf86cd799439011\")",
                "ObjectId(\"507f1f77bcf86cd799439011\")");
        DataModifyUnit rawUnit = unit("_raw_json", raw, raw);
        DataModifyUnit score = unit("score", "1", "2");
        DataModifyUnit flag = unit("flag", "true", "false");
        DataModifyUnit phone = unit("phone", "13800138000", "13900139000");
        DataModifyUnit flagStr = unit("flag_str", "true", "true");
        DataModifyUnit nullStr = unit("null_str", "null", "null");
        // flag_str / null_str unchanged → not in $set; only changed fields
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures",
                Arrays.asList(id, rawUnit, score, flag, phone, flagStr, nullStr));
        Assert.assertTrue(sql.contains("\"score\": 2"));
        Assert.assertFalse(sql.contains("\"score\": \"2\""));
        Assert.assertTrue(sql.contains("\"flag\": false"));
        Assert.assertFalse(sql.matches("(?s).*\"flag\":\\s*\"false\".*"));
        Assert.assertTrue(sql.contains("\"phone\": \"13900139000\""));
        // change string "true" / "null" fields to assert quoting
        DataModifyUnit flagStrEdit = unit("flag_str", "true", "false");
        DataModifyUnit nullStrEdit = unit("null_str", "null", "nullish");
        String sql2 = MongoUpdateGenerator.generate("workbench_edit_fixtures",
                Arrays.asList(id, rawUnit, flagStrEdit, nullStrEdit));
        Assert.assertTrue(sql2.contains("\"flag_str\": \"false\""));
        Assert.assertTrue(sql2.contains("\"null_str\": \"nullish\""));
    }

    /** AC-HF-010: string _id \"42\" from _raw_json must stay quoted (no numeric promotion). */
    @Test
    public void generate_unchangedRawJson_stringId42_staysQuoted() {
        String raw = "{\"_id\":\"42\",\"name\":\"old\"}";
        DataModifyUnit id = unit("_id", "42", "42");
        DataModifyUnit rawUnit = unit("_raw_json", raw, raw);
        DataModifyUnit name = unit("name", "old", "new");
        String sql = MongoUpdateGenerator.generate("workbench_edit_fixtures",
                Arrays.asList(id, rawUnit, name));
        Assert.assertTrue(sql.contains("_id: \"42\""));
        Assert.assertFalse(sql.contains("_id: 42 }") || sql.contains("_id: 42,"));
        Assert.assertTrue(sql.contains("\"name\": \"new\""));
    }

    private static DataModifyUnit unit(String column, String oldData, String newData) {
        DataModifyUnit unit = new DataModifyUnit();
        unit.setColumnName(column);
        unit.setOldData(oldData);
        unit.setNewData(newData);
        return unit;
    }
}
