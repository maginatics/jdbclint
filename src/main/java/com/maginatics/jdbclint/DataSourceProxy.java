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
import java.util.Properties;

import javax.sql.DataSource;

/** DataSourceProxy proxies a DataSource adding some checks. */
public final class DataSourceProxy implements InvocationHandler {
    private final DataSource dataSource;
    private final Properties properties;

    /**
     * Create a DataSourceProxy.
     *
     * @param dataSource DataSource to proxy
     * @param properties JDBC Lint configuration
     * @return proxied DataSource
     */
    public static DataSource newInstance(final DataSource dataSource,
            final Properties properties) {
        return (DataSource) Proxy.newProxyInstance(
                dataSource.getClass().getClassLoader(),
                new Class[] {DataSource.class},
                new DataSourceProxy(dataSource, properties));
    }

    private DataSourceProxy(final DataSource dataSource,
            final Properties properties) {
        this.dataSource = JdbcLint.checkNotNull(dataSource);
        this.properties = JdbcLint.checkNotNull(properties);
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        Object returnVal;
        try {
            returnVal = method.invoke(dataSource, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        if (method.getName().equals("getConnection")) {
            returnVal = ConnectionProxy.newInstance(
                    (Connection) returnVal, properties);
        }
        return returnVal;
    }
}
