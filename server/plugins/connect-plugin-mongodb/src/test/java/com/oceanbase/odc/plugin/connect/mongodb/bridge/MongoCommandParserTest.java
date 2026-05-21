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

import org.junit.Assert;
import org.junit.Test;

public class MongoCommandParserTest {
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
}
