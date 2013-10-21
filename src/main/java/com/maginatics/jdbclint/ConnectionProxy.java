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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ConnectionProxy proxies a Connection adding some checks.
 *
 *     * whether Connection was closed
 *     * whether Connection was committed or rolled back
 */
public final class ConnectionProxy implements InvocationHandler {
    private final Connection conn;
    private final Properties properties;
    private final Exception exception = new SQLException();

    private boolean closed = false;
    private boolean committedOrRolledBack = true;
    private boolean expectPrepareStatement = true;

    private final boolean checkDoubleClose;
    private final boolean checkExpectPrepareStatement;
    private final boolean checkMissingClose;
    private final boolean checkMissingCommitOrRollback;

    /**
     * Create a ConnectionProxy.
     *
     * @param conn Connection to proxy
     * @return proxied Connection
     */
    public static Connection newInstance(final Connection conn) {
        return newInstance(conn, System.getProperties());
    }

    /**
     * Create a ConnectionProxy.
     *
     * @param conn Connection to proxy
     * @param properties JDBC Lint configuration
     * @return proxied Connection
     */
    public static Connection newInstance(final Connection conn,
            final Properties properties) {
        return (Connection) Proxy.newProxyInstance(
                conn.getClass().getClassLoader(),
                new Class[] {Connection.class},
                new ConnectionProxy(conn, properties));
    }

    private ConnectionProxy(final Connection conn,
            final Properties properties) {
        this.conn = JdbcLint.checkNotNull(conn);
        this.properties = JdbcLint.checkNotNull(properties);

        checkDoubleClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.CONNECTION_DOUBLE_CLOSE));
        checkExpectPrepareStatement = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.CONNECTION_MISSING_PREPARE_STATEMENT));
        checkMissingClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.CONNECTION_MISSING_CLOSE));
        checkMissingCommitOrRollback = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.CONNECTION_MISSING_COMMIT_OR_ROLLBACK));
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("close")) {
            if (checkDoubleClose && closed) {
                JdbcLint.fail(properties, exception,
                        "Connection already closed");
            }
            closed = true;
            if (checkMissingCommitOrRollback &&
                    !conn.getAutoCommit() && !committedOrRolledBack) {
                conn.close();
                JdbcLint.fail(properties, exception,
                        "Connection did not commit or roll back");
            } else if (checkExpectPrepareStatement &&
                    expectPrepareStatement) {
                conn.close();
                JdbcLint.fail(properties, exception,
                        "Connection without prepareStatement");
            }
        } else if (name.equals("commit") || name.equals("rollback")) {
            committedOrRolledBack = true;
        }

        Object returnVal;
        try {
            returnVal = method.invoke(conn, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (name.equals("prepareStatement")) {
            committedOrRolledBack = false;
            expectPrepareStatement = false;
            returnVal = PreparedStatementProxy.newInstance(
                    (PreparedStatement) returnVal, properties);
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingClose && !closed) {
            JdbcLint.fail(properties, exception, "Connection not closed");
        }
    }
}
