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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

public class MongoCommandParserTest {
    @Test
    public void parseInsertMany_parsesDocumentArray() {
        MongoParsedCommand command = new MongoCommandParser().parse(
                "db.test_items.insertMany([ { _id: 1, name: \"alpha\", qty: 3, status: \"active\" }, "
                        + "{ _id: 2, name: \"beta\", qty: 5, status: \"active\" } ])");
        Assert.assertEquals(MongoParsedCommand.Type.INSERT_MANY, command.getType());
        Assert.assertEquals("test_items", command.getCollection());
        Assert.assertEquals(2, command.getDocuments().size());
        Assert.assertEquals("alpha", command.getDocuments().get(0).getString("name"));
        Assert.assertEquals(Integer.valueOf(5), command.getDocuments().get(1).getInteger("qty"));
    }

    @Test
    public void parseFind_returnsCollectionAndType() {
        MongoParsedCommand command = new MongoCommandParser().parse("db.users.find({name:'alice'})");
        Assert.assertEquals(MongoParsedCommand.Type.FIND, command.getType());
        Assert.assertEquals("users", command.getCollection());
        Assert.assertEquals("alice", command.getDocument().getString("name"));
    }

    @Test
    public void parseRunCommand_returnsPingDocument() {
        MongoParsedCommand command = new MongoCommandParser().parse("db.runCommand({ ping: 1 })");
        Assert.assertEquals(MongoParsedCommand.Type.RUN_COMMAND, command.getType());
        Assert.assertEquals(1, command.getDocument().getInteger("ping").intValue());
    }

    @Test
    public void parseKeepAliveSelect_returnsPingDocument() {
        MongoParsedCommand command = new MongoCommandParser().parse("SELECT 1");
        Assert.assertEquals(MongoParsedCommand.Type.RUN_COMMAND, command.getType());
        Assert.assertEquals(1, command.getDocument().getInteger("ping").intValue());
    }

    @Test
    public void parseCommentPrefixedRunCommand_returnsPingDocument() {
        MongoParsedCommand command = new MongoCommandParser().parse("/* keepalive */ db.runCommand({ ping: 1 });");
        Assert.assertEquals(MongoParsedCommand.Type.RUN_COMMAND, command.getType());
        Assert.assertEquals(1, command.getDocument().getInteger("ping").intValue());
    }

    @Test
    public void parseCommentSuffixedRunCommand_returnsPingDocument() {
        MongoParsedCommand command = new MongoCommandParser().parse("db.runCommand({ ping: 1 }); /* keepalive */");
        Assert.assertEquals(MongoParsedCommand.Type.RUN_COMMAND, command.getType());
        Assert.assertEquals(1, command.getDocument().getInteger("ping").intValue());
    }

    @Test
    public void parseInsertOne_newDateWithoutArgs_parsesCurrentDate() {
        MongoParsedCommand command = new MongoCommandParser().parse(
                "db.agent_sessions.insertOne({ sessionId: \"s1\", createdAt: new Date() })");
        Assert.assertEquals(MongoParsedCommand.Type.INSERT_ONE, command.getType());
        Assert.assertTrue(command.getDocument().get("createdAt") instanceof Date);
    }

    @Test
    public void parseInsertOne_newDateWithExpression_parsesFutureDate() {
        long offsetMillis = 30L * 24 * 3600 * 1000;
        MongoParsedCommand command = new MongoCommandParser().parse(
                "db.agent_sessions.insertOne({ sessionId: \"s2\", expireAt: new Date(Date.now() + "
                        + offsetMillis + ") })");
        Date expireAt = (Date) command.getDocument().get("expireAt");
        Assert.assertTrue(expireAt.getTime() >= System.currentTimeMillis() + offsetMillis - 5000);
    }

    @Test
    public void parseInsertOne_nestedNewDateAndIsoDate_parsesDocument() {
        MongoParsedCommand command = new MongoCommandParser().parse(
                "db.agent_sessions.insertOne({ sessionId: \"s3\", messages: [{ createdAt: new Date() }], "
                        + "expireAt: ISODate(\"2030-01-01T00:00:00.000Z\") })");
        Document document = command.getDocument();
        Assert.assertTrue(document.getList("messages", Document.class).get(0).get("createdAt") instanceof Date);
        Date expireAt = (Date) document.get("expireAt");
        Assert.assertEquals(Instant.parse("2030-01-01T00:00:00.000Z").toEpochMilli(), expireAt.getTime());
    }

    @Test
    public void parseReplaceOne_andUpdateOne_rewritesObjectId() {
        MongoCommandParser parser = new MongoCommandParser();
        MongoParsedCommand replace = parser.parse(
                "db.workbench_edit_fixtures.replaceOne({ _id: ObjectId(\"507f1f77bcf86cd799439011\") }, "
                        + "{ \"_id\": {\"$oid\":\"507f1f77bcf86cd799439011\"}, \"name\": \"scalar-doc\" })");
        Assert.assertEquals(MongoParsedCommand.Type.REPLACE_ONE, replace.getType());
        Assert.assertEquals("workbench_edit_fixtures", replace.getCollection());
        Document filter = parser.parseDocument(replace.getRawArgs().get(0));
        Assert.assertTrue(filter.get("_id") instanceof org.bson.types.ObjectId);

        MongoParsedCommand update = parser.parse(
                "db.workbench_edit_fixtures.updateOne({ _id: ObjectId(\"507f1f77bcf86cd799439011\") }, "
                        + "{ $set: { score: null } })");
        Assert.assertEquals(MongoParsedCommand.Type.UPDATE_ONE, update.getType());
        Document updateDoc = parser.parseDocument(update.getRawArgs().get(1));
        Assert.assertTrue(updateDoc.containsKey("$set"));
        Assert.assertNull(((Document) updateDoc.get("$set")).get("score"));
    }
}
