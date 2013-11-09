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
import java.sql.SQLException;
import java.util.Properties;

/**
 * BlobProxy proxies a Blob adding some checks.
 *
 *     * whether Blob was freed
 *     * whether Blob was freed more than once
 */
final class BlobProxy implements InvocationHandler {
    private final Blob blob;
    private final Properties properties;
    private final Exception exception = new SQLException();

    private boolean freed = false;

    private final boolean checkDoubleFree;
    private final boolean checkMissingFree;

    static Blob newInstance(final Blob blob,
            final Properties properties) {
        return (Blob) Proxy.newProxyInstance(
                blob.getClass().getClassLoader(),
                new Class<?>[] {Blob.class},
                new BlobProxy(blob, properties));
    }

    BlobProxy(final Blob blob, final Properties properties) {
        this.blob = JdbcLint.checkNotNull(blob);
        this.properties = JdbcLint.checkNotNull(properties);

        checkDoubleFree = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.BLOB_DOUBLE_FREE));
        checkMissingFree = JdbcLint.nullEmptyOrTrue(properties.getProperty(
                JdbcLint.BLOB_MISSING_FREE));
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("free")) {
            if (checkDoubleFree && freed) {
                JdbcLint.fail(properties, exception,
                        "Blob already freed");
            }
            freed = true;
        }

        Object returnVal;
        try {
            returnVal = method.invoke(blob, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
        return returnVal;
    }

    @Override
    protected void finalize() throws SQLException {
        if (checkMissingFree && !freed) {
            JdbcLint.fail(properties, exception, "Blob not freed");
        }
    }
}
