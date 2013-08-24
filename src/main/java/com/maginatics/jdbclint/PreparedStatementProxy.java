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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * PreparedStatementProxy proxies a PreparedStatement adding some checks.
 *
 *     * whether Statment was closed
 *     * whether Statment was closed more than once
 */
final class PreparedStatementProxy implements InvocationHandler {
    private final PreparedStatement stmt;
    private final Properties properties;
    private final Exception exception = new SQLException();

    private boolean closed = false;
    private boolean expectExecute = true;
    private boolean expectExecuteBatch = false;

    private final boolean checkDoubleClose;
    private final boolean checkMissingClose;
    private final boolean checkMissingExecute;
    private final boolean checkMissingExecuteBatch;

    /**
     * Create a PreparedStatementProxy.
     *
     * @param stmt PreparedStatement to proxy
     * @return proxied PreparedStatement
     */
    static PreparedStatement newInstance(final PreparedStatement stmt) {
        return newInstance(stmt, System.getProperties());
    }

    static PreparedStatement newInstance(final PreparedStatement stmt,
            final Properties properties) {
        return (PreparedStatement) Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                new Class[] {PreparedStatement.class},
                new PreparedStatementProxy(stmt, properties));
    }

    private PreparedStatementProxy(final PreparedStatement stmt,
            final Properties properties) {
        this.stmt = JdbcLint.checkNotNull(stmt);
        this.properties = JdbcLint.checkNotNull(properties);

        checkDoubleClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.PREPARED_STATEMENT_DOUBLE_CLOSE));
        checkMissingClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.PREPARED_STATEMENT_MISSING_CLOSE));
        checkMissingExecute = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.PREPARED_STATEMENT_MISSING_EXECUTE));
        checkMissingExecuteBatch = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.PREPARED_STATEMENT_MISSING_EXECUTE_BATCH));
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("addBatch")) {
            expectExecute = false;
            expectExecuteBatch = true;
        } else if (name.equals("executeBatch")) {
            expectExecuteBatch = false;
        } else if (name.startsWith("execute")) {
            expectExecute = false;
        } else if (name.equals("prepareStatement")) {
            expectExecute = true;
        } else if (name.equals("close")) {
            if (checkDoubleClose && closed) {
                // Closing the same statement twice can cause issues with
                // server-side prepared statements.
                JdbcLint.fail(properties, exception,
                        "PreparedStatement already closed");
            } else if (checkMissingExecute && expectExecute) {
                JdbcLint.fail(properties, exception,
                        "PreparedStatement without execute");
            } else if (checkMissingExecuteBatch && expectExecuteBatch) {
                JdbcLint.fail(properties, exception,
                        "PreparedStatement addBatch without executeBatch");
            }
            closed = true;
        }

        Object returnVal;
        try {
            returnVal = method.invoke(stmt, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (name.equals("executeQuery") || name.equals("getGeneratedKeys")) {
            if (returnVal != null) {
                returnVal = ResultSetProxy.newInstance((ResultSet) returnVal,
                        properties);
            }
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingClose && !closed) {
            JdbcLint.fail(properties, exception,
                    "PreparedStatement not closed");
        }
    }
}
