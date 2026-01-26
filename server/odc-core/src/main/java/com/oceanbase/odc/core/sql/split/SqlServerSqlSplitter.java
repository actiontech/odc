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
package com.oceanbase.odc.core.sql.split;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.shared.PreConditions;

/**
 * SQL Server sql script splitter.
 *
 * <p>
 * Features: - Treat line-based {@code GO} as a batch separator (not sent to server). - Avoid
 * splitting by delimiter inside {@code BEGIN...END} block and {@code CASE...END}.
 * </p>
 */
public class SqlServerSqlSplitter {

    private static final String DEFAULT_DELIMITER = ";";

    private final String delimiter;

    public SqlServerSqlSplitter(String delimiter) {
        this.delimiter = StringUtils.isBlank(delimiter) ? DEFAULT_DELIMITER : delimiter;
    }

    public List<OffsetString> split(String sql) {
        if (StringUtils.isBlank(sql)) {
            return new ArrayList<>();
        }
        PreConditions.notBlank(delimiter, "delimiter", "Empty or blank delimiter is not allowed");

        final int n = sql.length();
        final List<OffsetString> out = new ArrayList<>();

        // parser states
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBracketIdent = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        // line tracking (for GO)
        int lineStart = 0;

        // construct stack to match END (BEGIN...END / CASE...END)
        Deque<Construct> constructs = new ArrayDeque<>();

        int stmtStart = 0;
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    lineStart = i + 1;
                }
                i++;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (inSingleQuote) {
                if (c == '\'') {
                    if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                        // escaped ''
                        i += 2;
                        continue;
                    }
                    inSingleQuote = false;
                }
                i++;
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
                i++;
                continue;
            }
            if (inBracketIdent) {
                if (c == ']') {
                    if (i + 1 < n && sql.charAt(i + 1) == ']') {
                        // escaped ]]
                        i += 2;
                        continue;
                    }
                    inBracketIdent = false;
                }
                i++;
                continue;
            }

            // start of line: check GO (only when not inside any construct)
            if (i == lineStart && constructs.isEmpty()) {
                int goLineEnd = matchGoLine(sql, i);
                if (goLineEnd >= 0) {
                    // flush previous statement, GO line itself is not included
                    addIfNotBlank(out, sql, stmtStart, i);
                    stmtStart = goLineEnd;
                    i = goLineEnd;
                    lineStart = goLineEnd;
                    continue;
                }
            }

            // comment start
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i += 2;
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }

            // quote start
            if (c == '\'') {
                inSingleQuote = true;
                i++;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                i++;
                continue;
            }
            if (c == '[') {
                inBracketIdent = true;
                i++;
                continue;
            }

            // delimiter split (only when not inside any construct)
            if (constructs.isEmpty() && isPrefix(sql, i, delimiter)) {
                addIfNotBlank(out, sql, stmtStart, i);
                i += delimiter.length();
                stmtStart = i;
                continue;
            }

            // keyword scan for BEGIN/CASE/END
            if (isAlpha(c)) {
                int j = i + 1;
                while (j < n && isAlpha(sql.charAt(j))) {
                    j++;
                }
                String word = sql.substring(i, j);
                String upper = word.toUpperCase();

                if ("BEGIN".equals(upper)) {
                    // ignore BEGIN TRAN/TRANSACTION/DISTRIBUTED (no matching END)
                    String next = peekNextWord(sql, j);
                    if (!("TRAN".equals(next) || "TRANSACTION".equals(next) || "DISTRIBUTED".equals(next))) {
                        constructs.push(Construct.BEGIN);
                    }
                } else if ("CASE".equals(upper)) {
                    constructs.push(Construct.CASE);
                } else if ("END".equals(upper)) {
                    if (!constructs.isEmpty()) {
                        constructs.pop();
                    }
                }

                i = j;
                continue;
            }

            if (c == '\n') {
                lineStart = i + 1;
            }
            i++;
        }

        addIfNotBlank(out, sql, stmtStart, n);
        return out;
    }

    public static SqlStatementIterator iterator(InputStream input, Charset charset, String delimiter) {
        PreConditions.notNull(input, "input");
        PreConditions.notNull(charset, "charset");
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(delimiter);
        String sql = readAll(input, charset);
        List<OffsetString> stmts = splitter.split(sql);
        return new ListSqlStatementIterator(stmts);
    }

    private static int matchGoLine(String sql, int lineStart) {
        // Match: [whitespace] GO [whitespace] [optional integer] [whitespace] [optional comment] [line end]
        final int n = sql.length();
        int i = lineStart;
        while (i < n && (sql.charAt(i) == ' ' || sql.charAt(i) == '\t' || sql.charAt(i) == '\r')) {
            i++;
        }
        if (i + 2 > n) {
            return -1;
        }
        if (!equalsIgnoreCase(sql, i, "GO")) {
            return -1;
        }
        int j = i + 2;
        // must be word boundary
        if (j < n && isAlpha(sql.charAt(j))) {
            return -1;
        }
        while (j < n && (sql.charAt(j) == ' ' || sql.charAt(j) == '\t' || sql.charAt(j) == '\r')) {
            j++;
        }
        // optional integer repetition count (GO 10)
        while (j < n && Character.isDigit(sql.charAt(j))) {
            j++;
        }
        while (j < n && (sql.charAt(j) == ' ' || sql.charAt(j) == '\t' || sql.charAt(j) == '\r')) {
            j++;
        }
        // optional comment
        if (j < n && sql.charAt(j) == '-' && j + 1 < n && sql.charAt(j + 1) == '-') {
            // rest of line is comment
            while (j < n && sql.charAt(j) != '\n') {
                j++;
            }
        } else if (j < n && sql.charAt(j) == '/' && j + 1 < n && sql.charAt(j + 1) == '*') {
            // allow block comment till line end, but block comment may span lines; still treat GO line only if
            // comment closes on same line
            int k = j + 2;
            boolean closed = false;
            while (k + 1 < n) {
                if (sql.charAt(k) == '*' && sql.charAt(k + 1) == '/') {
                    closed = true;
                    k += 2;
                    break;
                }
                if (sql.charAt(k) == '\n') {
                    return -1;
                }
                k++;
            }
            if (!closed) {
                return -1;
            }
            j = k;
            while (j < n && (sql.charAt(j) == ' ' || sql.charAt(j) == '\t' || sql.charAt(j) == '\r')) {
                j++;
            }
        }
        // now must end line
        if (j < n && sql.charAt(j) != '\n') {
            return -1;
        }
        // consume line end
        if (j < n && sql.charAt(j) == '\n') {
            return j + 1;
        }
        return n;
    }

    private static boolean equalsIgnoreCase(String s, int offset, String targetUpper) {
        if (offset + targetUpper.length() > s.length()) {
            return false;
        }
        for (int i = 0; i < targetUpper.length(); i++) {
            char c = s.charAt(offset + i);
            if (Character.toUpperCase(c) != targetUpper.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrefix(String s, int offset, String prefix) {
        if (offset + prefix.length() > s.length()) {
            return false;
        }
        return s.startsWith(prefix, offset);
    }

    private static boolean isAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static String peekNextWord(String sql, int offset) {
        final int n = sql.length();
        int i = offset;
        while (i < n && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < n && isAlpha(sql.charAt(j))) {
            j++;
        }
        if (j > i) {
            return sql.substring(i, j).toUpperCase();
        }
        return "";
    }

    private static void addIfNotBlank(List<OffsetString> out, String sql, int start, int end) {
        if (end <= start) {
            return;
        }
        String segment = sql.substring(start, end);
        if (StringUtils.isBlank(segment)) {
            return;
        }
        out.add(new OffsetString(start, segment));
    }

    private static String readAll(InputStream input, Charset charset) {
        try (Reader reader = new InputStreamReader(input, charset)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) >= 0) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read sql input", e);
        }
    }

    private enum Construct {
        BEGIN,
        CASE
    }

    private static class ListSqlStatementIterator implements SqlStatementIterator {

        private final Iterator<OffsetString> it;

        private OffsetString current;

        private long iteratedBytes = 0;

        private ListSqlStatementIterator(List<OffsetString> stmts) {
            this.it = stmts.iterator();
        }

        @Override
        public boolean hasNext() {
            if (current == null && it.hasNext()) {
                current = it.next();
                iteratedBytes = Math.max(iteratedBytes, (long) current.getOffset() + current.getStr().length());
            }
            return current != null;
        }

        @Override
        public OffsetString next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more available sql.");
            }
            OffsetString next = current;
            current = null;
            return next;
        }

        @Override
        public long iteratedBytes() {
            return iteratedBytes;
        }
    }
}

