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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicBoolean readOnly = new AtomicBoolean(true);

    private enum State {
        OPENED,
        IN_TRANSACTION,
        COMMITTED,
        CLOSED;
    }
    private final AtomicReference<State> state =
            new AtomicReference<State>(State.OPENED);

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
                    state.get() == State.CLOSED) {
                Utils.fail(config, exception, "Connection already closed");
            } else if (config.isEnabled(
                            Check.CONNECTION_MISSING_COMMIT_OR_ROLLBACK) &&
                    !conn.getAutoCommit() &&
                    state.compareAndSet(State.IN_TRANSACTION, State.CLOSED)) {
                conn.close();
                Utils.fail(config, exception,
                        "Connection did not commit or roll back");
            } else if (config.isEnabled(
                            Check.CONNECTION_MISSING_PREPARE_STATEMENT) &&
                    state.compareAndSet(State.OPENED, State.CLOSED)) {
                conn.close();
                Utils.fail(config, exception,
                        "Connection without prepareStatement");
            }
            state.set(State.CLOSED);
            if (config.isEnabled(Check.CONNECTION_MISSING_READ_ONLY) &&
                isReadOnly() && !conn.isReadOnly()) {
                conn.close();
                Utils.fail(config, exception,
                    "Connection did not execute updates, " +
                    "consider calling setReadOnly");
            }
            return null;
        }
        if (name.equals("commit") || name.equals("rollback")) {
            state.set(State.COMMITTED);
        }

        Object returnVal;
        try {
            returnVal = method.invoke(conn, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (name.equals("createStatement")) {
            state.set(State.IN_TRANSACTION);
            returnVal = StatementProxy.newInstance(this,
                    (Statement) returnVal, config);
        } else if (name.equals("prepareStatement")) {
            state.set(State.IN_TRANSACTION);
            returnVal = StatementProxy.newInstance(this,
                    (PreparedStatement) returnVal, config);
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (config.isEnabled(Check.CONNECTION_MISSING_CLOSE) &&
                state.get() != State.CLOSED) {
            Utils.fail(config, exception, "Connection not closed");
        }
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    public boolean isReadOnly() {
        return readOnly.get();
    }
}
