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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.maginatics.jdbclint.Configuration.Check;

/**
 * StatementProxy proxies a Statement adding some checks.
 *
 *     * whether Statement was closed
 *     * whether Statement was closed more than once
 *     * whether Statement addBatch was called without executeBatch
 */
final class StatementProxy implements InvocationHandler {
    private final Statement stmt;
    private final Configuration config;
    private final String className;
    private final Exception exception = new SQLException();

    private enum State {
        OPENED,
        IN_ADD_BATCH,
        EXECUTED,
        CLOSED;
    }
    private State state = State.OPENED;

    private final boolean checkDoubleClose;
    private final boolean checkMissingClose;
    private final boolean checkMissingExecute;
    private final boolean checkMissingExecuteBatch;
    private final ConnectionProxy connectionProxy;

    static Statement newInstance(final ConnectionProxy connectionProxy,
            final Statement stmt, final Configuration config) {
        return (Statement) Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                new Class<?>[] {Statement.class},
                new StatementProxy(connectionProxy, stmt, config));
    }

    static PreparedStatement newInstance(final ConnectionProxy connectionProxy,
            final PreparedStatement stmt, final Configuration config) {
        return (PreparedStatement) Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                new StatementProxy(connectionProxy, stmt, config));
    }

    StatementProxy(final ConnectionProxy connectionProxy, final Statement stmt,
            final Configuration config) {
        this.connectionProxy = Utils.checkNotNull(connectionProxy);
        this.stmt = Utils.checkNotNull(stmt);
        this.config = Utils.checkNotNull(config);
        this.className = "Statement";

        checkDoubleClose = config.isEnabled(Check.STATEMENT_DOUBLE_CLOSE);
        checkMissingClose = config.isEnabled(Check.STATEMENT_MISSING_CLOSE);
        checkMissingExecute = config.isEnabled(Check.STATEMENT_MISSING_EXECUTE);
        checkMissingExecuteBatch = config.isEnabled(
                Check.STATEMENT_MISSING_EXECUTE_BATCH);
    }

    StatementProxy(final ConnectionProxy connectionProxy,
            final PreparedStatement stmt, final Configuration config) {
        this.connectionProxy = Utils.checkNotNull(connectionProxy);
        this.stmt = Utils.checkNotNull(stmt);
        this.config = Utils.checkNotNull(config);
        this.className = "PreparedStatement";

        checkDoubleClose = config.isEnabled(
                Check.PREPARED_STATEMENT_DOUBLE_CLOSE);
        checkMissingClose = config.isEnabled(
                Check.PREPARED_STATEMENT_MISSING_CLOSE);
        checkMissingExecute = config.isEnabled(
                Check.PREPARED_STATEMENT_MISSING_EXECUTE);
        checkMissingExecuteBatch = config.isEnabled(
                Check.PREPARED_STATEMENT_MISSING_EXECUTE_BATCH);
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("addBatch")) {
            state = State.IN_ADD_BATCH;
        } else if (name.equals("executeBatch") ||
                name.equals("executeLargeBatch")) {
            state = State.EXECUTED;
        } else if (name.equals("execute") ||
                name.equals("executeLargeUpdate") ||
                name.equals("executeQuery") ||
                name.equals("executeUpdate")) {
            state = State.EXECUTED;
        } else if (name.equals("close")) {
            if (checkDoubleClose && state == State.CLOSED) {
                // Closing the same statement twice can cause issues with
                // server-side statements.
                Utils.fail(config, exception, className + " already closed");
            } else if (checkMissingExecute && state == State.OPENED) {
                state = State.CLOSED;
                stmt.close();
                Utils.fail(config, exception, className + " without execute");
            } else if (checkMissingExecuteBatch &&
                    state == State.IN_ADD_BATCH) {
                state = State.CLOSED;
                stmt.close();
                Utils.fail(config, exception,
                        className + " addBatch without executeBatch");
            }
            state = State.CLOSED;
        }

        // Be conservative and mark connection as non-readonly for all execute
        // calls except executeQuery
        if (name.startsWith("execute") && !name.equals("executeQuery")) {
            connectionProxy.setReadOnly(false);
        }

        Object returnVal;
        try {
            returnVal = method.invoke(stmt, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (name.equals("executeQuery") || name.equals("getGeneratedKeys") ||
                name.equals("getResultSet")) {
            if (returnVal != null) {
                returnVal = ResultSetProxy.newInstance((ResultSet) returnVal,
                        config);
            }
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingClose && state != State.CLOSED) {
            Utils.fail(config, exception, className + " not closed");
        }
    }
}
