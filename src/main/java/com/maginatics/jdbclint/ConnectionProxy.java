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
import java.sql.Statement;

import com.maginatics.jdbclint.Configuration.Check;

/**
 * ConnectionProxy proxies a Connection adding some checks.
 *
 *     * whether Connection was closed
 *     * whether Connection was committed or rolled back
 */
public final class ConnectionProxy implements InvocationHandler {
    private final Connection conn;
    private final Configuration config;
    private final Exception exception = new SQLException();

    private enum State {
        OPENED,
        IN_TRANSACTION,
        COMMITTED,
        CLOSED;
    }
    private State state = State.OPENED;

    /**
     * Create a ConnectionProxy.
     *
     * @param conn Connection to proxy
     * @param config configuration
     * @return proxied Connection
     */
    public static Connection newInstance(final Connection conn,
            final Configuration config) {
        return (Connection) Proxy.newProxyInstance(
                conn.getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                new ConnectionProxy(conn, config));
    }

    ConnectionProxy(final Connection conn, final Configuration config) {
        this.conn = Utils.checkNotNull(conn);
        this.config = Utils.checkNotNull(config);
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("close")) {
            if (config.isEnabled(Check.CONNECTION_DOUBLE_CLOSE) &&
                    state == State.CLOSED) {
                Utils.fail(config, exception, "Connection already closed");
            } else if (config.isEnabled(
                            Check.CONNECTION_MISSING_COMMIT_OR_ROLLBACK) &&
                    !conn.getAutoCommit() && state == State.IN_TRANSACTION) {
                state = State.CLOSED;
                conn.close();
                Utils.fail(config, exception,
                        "Connection did not commit or roll back");
            } else if (config.isEnabled(
                            Check.CONNECTION_MISSING_PREPARE_STATEMENT) &&
                    state == State.OPENED) {
                state = State.CLOSED;
                conn.close();
                Utils.fail(config, exception,
                        "Connection without prepareStatement");
            }
            state = State.CLOSED;
        } else if (name.equals("commit") || name.equals("rollback")) {
            state = State.COMMITTED;
        }

        Object returnVal;
        try {
            returnVal = method.invoke(conn, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (name.equals("createStatement")) {
            state = State.IN_TRANSACTION;
            returnVal = StatementProxy.newInstance(
                    (Statement) returnVal, config);
        } else if (name.equals("prepareStatement")) {
            state = State.IN_TRANSACTION;
            returnVal = StatementProxy.newInstance(
                    (PreparedStatement) returnVal, config);
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (config.isEnabled(Check.CONNECTION_MISSING_CLOSE) &&
                state != State.CLOSED) {
            Utils.fail(config, exception, "Connection not closed");
        }
    }
}
