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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * StatementProxy proxies a Statement adding some checks.
 *
 *     * whether Statement was closed
 *     * whether Statement was closed more than once
 *     * whether Statement addBatch was called without executeBatch
 */
// TODO: unify with PreparedStatementProxy?
final class StatementProxy implements InvocationHandler {
    private final Statement stmt;
    private final Properties properties;
    private final Exception exception = new SQLException();

    private boolean closed = false;
    private boolean expectExecute = true;
    private boolean expectExecuteBatch = false;

    private final boolean checkDoubleClose;
    private final boolean checkMissingClose;
    private final boolean checkMissingExecute;
    private final boolean checkMissingExecuteBatch;

    static Statement newInstance(final Statement stmt,
            final Properties properties) {
        return (Statement) Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                new Class<?>[] {Statement.class},
                new StatementProxy(stmt, properties));
    }

    private StatementProxy(final Statement stmt,
            final Properties properties) {
        this.stmt = JdbcLint.checkNotNull(stmt);
        this.properties = JdbcLint.checkNotNull(properties);

        checkDoubleClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.STATEMENT_DOUBLE_CLOSE));
        checkMissingClose = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.STATEMENT_MISSING_CLOSE));
        checkMissingExecute = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.STATEMENT_MISSING_EXECUTE));
        checkMissingExecuteBatch = JdbcLint.nullEmptyOrTrue(
                properties.getProperty(
                        JdbcLint.STATEMENT_MISSING_EXECUTE_BATCH));
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
                // server-side statements.
                JdbcLint.fail(properties, exception,
                        "Statement already closed");
            }
            closed = true;
            if (checkMissingExecute && expectExecute) {
                stmt.close();
                JdbcLint.fail(properties, exception,
                        "Statement without execute");
            } else if (checkMissingExecuteBatch && expectExecuteBatch) {
                stmt.close();
                JdbcLint.fail(properties, exception,
                        "Statement addBatch without executeBatch");
            }
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
                        properties);
            }
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingClose && !closed) {
            JdbcLint.fail(properties, exception,
                    "Statement not closed");
        }
    }
}
