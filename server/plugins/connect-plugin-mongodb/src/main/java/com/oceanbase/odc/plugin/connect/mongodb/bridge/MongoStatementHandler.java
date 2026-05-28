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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class MongoStatementHandler implements InvocationHandler {
    private final Connection connection;
    private final MongoSessionContext context;
    private final MongoCommandParser parser = new MongoCommandParser();
    private final MongoResultMapper resultMapper = new MongoResultMapper();
    private ResultSet currentResultSet;
    private int updateCount = -1;
    private int queryTimeout;
    private int maxRows;

    MongoStatementHandler(Connection connection, MongoSessionContext context) {
        this.connection = connection;
        this.context = context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        if ("toString".equals(name)) {
            return "MongoJdbcStatement[" + context.getConnectionId() + "]";
        }
        if ("execute".equals(name)) {
            return execute((String) args[0]);
        }
        if ("getResultSet".equals(name)) {
            return currentResultSet;
        }
        if ("getUpdateCount".equals(name)) {
            return updateCount;
        }
        if ("getMoreResults".equals(name)) {
            return false;
        }
        if ("setQueryTimeout".equals(name)) {
            queryTimeout = (Integer) args[0];
            return null;
        }
        if ("getQueryTimeout".equals(name)) {
            return queryTimeout;
        }
        if ("setMaxRows".equals(name)) {
            maxRows = (Integer) args[0];
            return null;
        }
        if ("getMaxRows".equals(name)) {
            return maxRows;
        }
        if ("getConnection".equals(name)) {
            return connection;
        }
        if ("close".equals(name)) {
            currentResultSet = null;
            return null;
        }
        if ("isClosed".equals(name)) {
            return false;
        }
        if ("unwrap".equals(name)) {
            if (((Class<?>) args[0]).isInstance(proxy)) {
                return proxy;
            }
            throw new SQLException("Unsupported unwrap target");
        }
        if ("isWrapperFor".equals(name)) {
            return ((Class<?>) args[0]).isInstance(proxy);
        }
        throw new UnsupportedOperationException("Unsupported MongoDB JDBC statement method: " + name);
    }

    private boolean execute(String sql) throws SQLException {
        MongoParsedCommand command;
        try {
            command = parser.parse(sql);
        } catch (IllegalArgumentException ex) {
            log.warn("Mongo statement handler failed to parse sql, rawSql={}", sql, ex);
            throw ex;
        }
        log.info("Mongo statement handler executing, sql={}, type={}, collection={}",
                sql, command.getType(), command.getCollection());
        switch (command.getType()) {
            case FIND:
                return setQueryResult(find(command));
            case AGGREGATE:
                return setQueryResult(aggregate(command));
            case INSERT_ONE:
                return setUpdateResult(insertOne(command));
            case INSERT_MANY:
                return setUpdateResult(insertMany(command));
            case UPDATE_ONE:
            case UPDATE_MANY:
                return setUpdateResult(update(command));
            case DELETE_ONE:
            case DELETE_MANY:
                return setUpdateResult(delete(command));
            case RUN_COMMAND:
                return setQueryResult(runCommand(command));
            default:
                throw new SQLException("Unsupported MongoDB command type");
        }
    }

    private boolean setQueryResult(MongoTabularResult result) throws SQLException {
        this.currentResultSet = MongoBridgeUtil.toResultSet(result);
        this.updateCount = -1;
        return true;
    }

    private boolean setUpdateResult(Document result) throws SQLException {
        this.currentResultSet = MongoBridgeUtil.toResultSet(resultMapper.mapWriteResult(result));
        this.updateCount = 1;
        return true;
    }

    private MongoTabularResult find(MongoParsedCommand command) {
        List<Document> rows = new ArrayList<>();
        MongoCollection<Document> collection = context.getDatabase().getCollection(command.getCollection());
        FindIterable<Document> iterable = collection.find(command.getDocument());
        if (maxRows > 0) {
            iterable = iterable.limit(maxRows);
        }
        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                rows.add(cursor.next());
            }
        }
        return resultMapper.mapDocuments(rows);
    }

    private MongoTabularResult aggregate(MongoParsedCommand command) {
        List<Document> rows = new ArrayList<>();
        MongoCollection<Document> collection = context.getDatabase().getCollection(command.getCollection());
        List<org.bson.conversions.Bson> pipeline = new ArrayList<>(command.getPipeline());
        if (maxRows > 0) {
            pipeline.add(new Document("$limit", maxRows));
        }
        MongoCursor<Document> cursor = collection.aggregate(pipeline).iterator();
        try (MongoCursor<Document> closable = cursor) {
            while (closable.hasNext()) {
                rows.add(closable.next());
            }
        }
        return resultMapper.mapDocuments(rows);
    }

    private Document insertOne(MongoParsedCommand command) {
        InsertOneResult result = context.getDatabase().getCollection(command.getCollection())
                .insertOne(command.getDocument());
        return new Document("acknowledged", result.wasAcknowledged()).append("insertedId", result.getInsertedId());
    }

    private Document insertMany(MongoParsedCommand command) {
        InsertManyResult result = context.getDatabase().getCollection(command.getCollection())
                .insertMany(command.getDocuments());
        return new Document("acknowledged", result.wasAcknowledged())
                .append("insertedCount", result.getInsertedIds().size());
    }

    private Document update(MongoParsedCommand command) {
        Document filter = Document.parse(command.getRawArgs().get(0).replace('\'', '"'));
        Document update = Document.parse(command.getRawArgs().get(1).replace('\'', '"'));
        UpdateResult result;
        if (command.getType() == MongoParsedCommand.Type.UPDATE_MANY) {
            result = context.getDatabase().getCollection(command.getCollection()).updateMany(filter, update);
        } else {
            result = context.getDatabase().getCollection(command.getCollection()).updateOne(filter, update);
        }
        return new Document("acknowledged", result.wasAcknowledged())
                .append("matchedCount", result.getMatchedCount())
                .append("modifiedCount", result.getModifiedCount());
    }

    private Document delete(MongoParsedCommand command) {
        DeleteResult result;
        if (command.getType() == MongoParsedCommand.Type.DELETE_MANY) {
            result = context.getDatabase().getCollection(command.getCollection()).deleteMany(command.getDocument());
        } else {
            result = context.getDatabase().getCollection(command.getCollection()).deleteOne(command.getDocument());
        }
        return new Document("acknowledged", result.wasAcknowledged()).append("deletedCount", result.getDeletedCount());
    }

    private MongoTabularResult runCommand(MongoParsedCommand command) {
        return resultMapper.mapSingleDocument(context.getDatabase().runCommand(command.getDocument()));
    }
}
