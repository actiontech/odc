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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.shared.PreConditions;

/**
 * PostgreSQL SQL script splitter.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Supports dollar-quoting: $$...$$ and $tag$...$tag$</li>
 * <li>Supports E-string: E'...' with backslash escapes</li>
 * <li>Supports nested block comments (PG specific)</li>
 * <li>Supports double-quoted identifiers: "column_name"</li>
 * <li>Supports single-quoted strings with doubling escape: 'don''t'</li>
 * </ul>
 * </p>
 *
 * <p>
 * Dollar-quoting is a PostgreSQL-specific feature that allows string literals to contain any
 * characters without escaping, including newlines, quotes, and semicolons. This is commonly used in
 * function and procedure definitions.
 * </p>
 */
public class PostgreSqlSplitter {

    private static final String DEFAULT_DELIMITER = ";";

    private final String delimiter;

    public PostgreSqlSplitter() {
        this(DEFAULT_DELIMITER);
    }

    public PostgreSqlSplitter(String delimiter) {
        this.delimiter = StringUtils.isBlank(delimiter) ? DEFAULT_DELIMITER : delimiter;
    }

    /**
     * Split SQL script into individual statements.
     *
     * @param sql the SQL script to split
     * @return list of statements with their offsets
     */
    public List<OffsetString> split(String sql) {
        if (StringUtils.isBlank(sql)) {
            return new ArrayList<>();
        }
        PreConditions.notBlank(delimiter, "delimiter", "Empty or blank delimiter is not allowed");

        final int n = sql.length();
        final List<OffsetString> out = new ArrayList<>();

        // Parser states
        boolean inSingleQuote = false; // Single-quoted string '...'
        boolean inEString = false; // E-string E'...' (backslash escapes)
        boolean inDoubleQuote = false; // Double-quoted identifier "..."
        boolean inLineComment = false; // Line comment --
        boolean inBlockComment = false; // Block comment /* ... */
        boolean inDollarQuote = false; // Dollar-quoting $$...$$ or $tag$...$tag$

        // Dollar-quoting state
        String currentDollarTag = null; // Current dollar tag (empty string for $$)

        // Block comment nesting depth (PostgreSQL supports nested block comments)
        int blockCommentDepth = 0;

        // E-string backslash escape state
        boolean inBackslashEscape = false;

        int stmtStart = 0;
        int i = 0;

        while (i < n) {
            char c = sql.charAt(i);

            // === State: Line Comment ===
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                i++;
                continue;
            }

            // === State: Block Comment (with nesting support) ===
            if (inBlockComment) {
                if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                    // Nested block comment start
                    blockCommentDepth++;
                    i += 2;
                    continue;
                }
                if (c == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                    blockCommentDepth--;
                    if (blockCommentDepth == 0) {
                        inBlockComment = false;
                    }
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }

            // === State: Single-quoted String ===
            if (inSingleQuote) {
                if (c == '\'') {
                    if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                        // Escaped single quote by doubling ''
                        i += 2;
                        continue;
                    }
                    // End of single-quoted string
                    inSingleQuote = false;
                }
                i++;
                continue;
            }

            // === State: E-string (PostgreSQL extended string with backslash escapes) ===
            if (inEString) {
                if (inBackslashEscape) {
                    // After backslash, next character is escaped (including another backslash)
                    inBackslashEscape = false;
                    i++;
                    continue;
                }
                if (c == '\\') {
                    inBackslashEscape = true;
                    i++;
                    continue;
                }
                if (c == '\'') {
                    // Check for doubled single quote '' (also valid in E-string)
                    if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    // End of E-string
                    inEString = false;
                    inBackslashEscape = false;
                }
                i++;
                continue;
            }

            // === State: Double-quoted Identifier ===
            if (inDoubleQuote) {
                if (c == '"') {
                    if (i + 1 < n && sql.charAt(i + 1) == '"') {
                        // Escaped double quote by doubling ""
                        i += 2;
                        continue;
                    }
                    // End of double-quoted identifier
                    inDoubleQuote = false;
                }
                i++;
                continue;
            }

            // === State: Dollar-quoting ===
            if (inDollarQuote) {
                if (c == '$') {
                    String endTag = matchDollarTag(sql, i);
                    if (endTag != null && endTag.equals(currentDollarTag)) {
                        // Found matching end tag
                        inDollarQuote = false;
                        currentDollarTag = null;
                        i += endTag.length() + 2; // Skip $tag$
                        continue;
                    }
                }
                // Any character inside dollar-quoting (including semicolons) is literal
                i++;
                continue;
            }

            // === Normal State ===

            // Line comment start
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i += 2;
                continue;
            }

            // Block comment start
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                blockCommentDepth = 1;
                i += 2;
                continue;
            }

            // Single-quoted string start
            if (c == '\'') {
                inSingleQuote = true;
                i++;
                continue;
            }

            // E-string start: E' or e'
            if ((c == 'E' || c == 'e') && i + 1 < n && sql.charAt(i + 1) == '\'') {
                inEString = true;
                i += 2;
                continue;
            }

            // Double-quoted identifier start
            if (c == '"') {
                inDoubleQuote = true;
                i++;
                continue;
            }

            // Dollar-quoting start
            if (c == '$') {
                String tag = matchDollarTag(sql, i);
                if (tag != null) {
                    inDollarQuote = true;
                    currentDollarTag = tag;
                    i += tag.length() + 2; // Skip $tag$
                    continue;
                }
                // Standalone $ (not a dollar-quoting delimiter), continue
                i++;
                continue;
            }

            // Delimiter check - split happens here
            if (isPrefix(sql, i, delimiter)) {
                addIfNotBlank(out, sql, stmtStart, i + delimiter.length());
                i += delimiter.length();
                stmtStart = i;
                continue;
            }

            i++;
        }

        // Add remaining statement
        addIfNotBlank(out, sql, stmtStart, n);
        return out;
    }

    /**
     * Match a dollar-quoting tag at the given position.
     * 
     * <p>
     * Dollar-quoting format: $tag$ where tag is optional. If tag is empty, it's just $$. Tag
     * characters: letters, digits, underscores, but first character cannot be a digit.
     * </p>
     *
     * @param sql the SQL string
     * @param pos position where '$' is found
     * @return the tag string (empty string for $$), or null if not a valid dollar tag
     */
    private String matchDollarTag(String sql, int pos) {
        final int n = sql.length();

        // Current character must be '$'
        if (pos >= n || sql.charAt(pos) != '$') {
            return null;
        }

        // Scan for the closing '$'
        int j = pos + 1;
        while (j < n) {
            char c = sql.charAt(j);
            if (c == '$') {
                // Found closing $
                break;
            }
            if (!isDollarTagChar(c, j == pos + 1)) {
                // Invalid tag character
                return null;
            }
            j++;
        }

        // Must find closing $
        if (j >= n || sql.charAt(j) != '$') {
            return null;
        }

        // Tag is the content between the two $ signs
        return sql.substring(pos + 1, j);
    }

    /**
     * Check if character is valid for dollar tag.
     * 
     * @param c the character to check
     * @param isFirst true if this is the first character of the tag
     * @return true if valid dollar tag character
     */
    private boolean isDollarTagChar(char c, boolean isFirst) {
        if (Character.isLetter(c) || c == '_') {
            return true;
        }
        if (!isFirst && Character.isDigit(c)) {
            return true;
        }
        return false;
    }

    private static boolean isPrefix(String s, int offset, String prefix) {
        if (offset + prefix.length() > s.length()) {
            return false;
        }
        return s.startsWith(prefix, offset);
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

    /**
     * Create an iterator for streaming SQL statement parsing.
     *
     * @param input the input stream
     * @param charset the character set
     * @param delimiter the statement delimiter
     * @return an iterator over SQL statements
     */
    public static SqlStatementIterator iterator(InputStream input, Charset charset, String delimiter) {
        PreConditions.notNull(input, "input");
        PreConditions.notNull(charset, "charset");
        PostgreSqlSplitter splitter = new PostgreSqlSplitter(delimiter);
        String sql = readAll(input, charset);
        List<OffsetString> stmts = splitter.split(sql);
        return new ListSqlStatementIterator(stmts);
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

    /**
     * Iterator implementation based on a pre-split list.
     */
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
