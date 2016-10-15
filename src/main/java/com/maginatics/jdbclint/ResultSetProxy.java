/*
 * Copyright 2012 - 2014 Maginatics, Inc.
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.maginatics.jdbclint.Configuration.Check;

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
    private final Configuration config;
    private final SQLException exception = new SQLException();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<String> unreadColumns = new HashSet<String>();

    static ResultSet newInstance(final ResultSet rs,
            final Configuration config) {
        return (ResultSet) Proxy.newProxyInstance(
                rs.getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class},
                new ResultSetProxy(rs, config));
    }

    ResultSetProxy(final ResultSet rs, final Configuration config) {
        this.rs = Utils.checkNotNull(rs);
        this.config = Utils.checkNotNull(config);
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("close")) {
            boolean previouslyClosed = closed.getAndSet(true);
            if (config.isEnabled(Check.RESULT_SET_DOUBLE_CLOSE) &&
                    previouslyClosed) {
                Utils.fail(config, exception, "ResultSet already closed");
            }
            if (!unreadColumns.isEmpty()) {
                rs.close();
                checkUnreadColumns();
            }
            return null;
        }
        if (name.equals("next")) {
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
                returnVal = BlobProxy.newInstance((Blob) returnVal, config);
            }
            return returnVal;
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    @Override
    protected void finalize() throws SQLException {
        if (config.isEnabled(Check.RESULT_SET_MISSING_CLOSE) && !closed.get()) {
            Utils.fail(config, exception, "ResultSet not closed");
        }
    }

    private boolean next() throws SQLException {
        checkUnreadColumns();
        boolean result = rs.next();
        if (result) {
            if (config.isEnabled(Check.RESULT_SET_UNREAD_COLUMN)) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); ++i) {
                    unreadColumns.add(metaData.getColumnLabel(i).toLowerCase());
                }
            }
        }
        return result;
    }

    private void checkUnreadColumns() throws SQLException {
        if (!unreadColumns.isEmpty()) {
            Utils.fail(config, exception, "ResultSet has unread column: " +
                    unreadColumns.iterator().next());
        }
    }

    private String columnIndexToLabel(final int columnIndex)
            throws SQLException {
        return rs.getMetaData().getColumnLabel(columnIndex).toLowerCase();
    }
}
