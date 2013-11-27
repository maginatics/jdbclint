/*
 * Copyright 2012 - 2013 Maginatics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maginatics.jdbclint;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * ResultSetProxy proxies a ResultSet adding some checks.
 *
 *     * whether ResultSet was closed
 *     * whether all columns were consumed
 */
final class ResultSetProxy implements InvocationHandler {
    private static final Set<String> GETTERS =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "getArray",
                    "getAsciiStream",
                    "getBigDecimal",
                    "getBinaryStream",
                    "getBlob",
                    "getBoolean",
                    "getByte",
                    "getBytes",
                    "getCharacterStream",
                    "getClob",
                    "getDate",
                    "getDouble",
                    "getFloat",
                    "getInt",
                    "getLong",
                    "getNCharacterStream",
                    "getNClob",
                    "getNString",
                    "getObject",
                    "getRef",
                    "getRowId",
                    "getShort",
                    "getSQLXML",
                    "getString",
                    "getTime",
                    "getTimestamp",
                    "getUnicodeStream",
                    "getURL")));

    private final ResultSet rs;
    private final Properties properties;
    private final SQLException exception = new SQLException();

    private boolean allRowsConsumed = false;
    private boolean closed = false;
    private final Set<String> unreadColumns = new HashSet<String>();

    private final boolean checkDoubleClose;
    private final boolean checkMissingClose;
    private final boolean checkUnreadColumns;

    static ResultSet newInstance(final ResultSet rs,
            final Properties properties) {
        return (ResultSet) Proxy.newProxyInstance(
                rs.getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class},
                new ResultSetProxy(rs, properties));
    }

    ResultSetProxy(final ResultSet rs, final Properties properties) {
        this.rs = JdbcLint.checkNotNull(rs);
        this.properties = JdbcLint.checkNotNull(properties);

        checkDoubleClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.RESULT_SET_DOUBLE_CLOSE));
        checkMissingClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.RESULT_SET_MISSING_CLOSE));
        checkUnreadColumns = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.RESULT_SET_UNREAD_COLUMN));
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("clearWarnings")) {
            // multiplexing clearWarnings to disable unread column checking
            unreadColumns.clear();
        } else if (name.equals("close")) {
            if (checkDoubleClose && closed) {
                JdbcLint.fail(properties, new SQLException(exception),
                        "ResultSet already closed");
            }
            closed = true;
            if (!unreadColumns.isEmpty()) {
                rs.close();
                checkUnreadColumns();
            }
        } else if (name.equals("next")) {
            return next();
        } else if (GETTERS.contains(name)) {
            String columnLabel;
            if (args[0] instanceof Integer) {
                columnLabel = columnIndexToLabel((Integer) args[0]);
            } else {
                columnLabel = (String) args[0];
            }
            unreadColumns.remove(columnLabel);
        }
        try {
            Object returnVal = method.invoke(rs, args);
            if (name.equals("getBlob")) {
                returnVal = BlobProxy.newInstance((Blob) returnVal, properties);
            }
            return returnVal;
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingClose && !closed) {
            JdbcLint.fail(properties, exception, "ResultSet not closed");
        }
    }

    private boolean next() throws SQLException {
        checkUnreadColumns();
        boolean result = rs.next();
        if (result) {
            if (checkUnreadColumns) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); ++i) {
                    unreadColumns.add(metaData.getColumnLabel(i).toLowerCase());
                }
            }
        } else {
            allRowsConsumed = true;
        }
        return result;
    }

    private void checkUnreadColumns() throws SQLException {
        if (!unreadColumns.isEmpty()) {
            JdbcLint.fail(properties, new SQLException(exception),
                    "ResultSet has unread column: " +
                    unreadColumns.iterator().next());
        }
    }

    private String columnIndexToLabel(final int columnIndex)
            throws SQLException {
        return rs.getMetaData().getColumnLabel(columnIndex).toLowerCase();
    }
}
