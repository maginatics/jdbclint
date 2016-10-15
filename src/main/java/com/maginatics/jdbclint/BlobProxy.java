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
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.maginatics.jdbclint.Configuration.Check;

/**
 * BlobProxy proxies a Blob adding some checks.
 *
 *     * whether Blob was freed
 *     * whether Blob was freed more than once
 */
final class BlobProxy implements InvocationHandler {
    private final Blob blob;
    private final Configuration config;
    private final Exception exception = new SQLException();

    private final AtomicBoolean freed = new AtomicBoolean();

    static Blob newInstance(final Blob blob, final Configuration config) {
        return (Blob) Proxy.newProxyInstance(
                blob.getClass().getClassLoader(),
                new Class<?>[] {Blob.class},
                new BlobProxy(blob, config));
    }

    BlobProxy(final Blob blob, final Configuration config) {
        this.blob = Utils.checkNotNull(blob);
        this.config = Utils.checkNotNull(config);
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
            final Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("free")) {
            if (config.isEnabled(Check.BLOB_DOUBLE_FREE) && freed.get()) {
                Utils.fail(config, exception, "Blob already freed");
            }
            freed.set(true);
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
        if (config.isEnabled(Check.BLOB_MISSING_FREE) && !freed.get()) {
            Utils.fail(config, exception, "Blob not freed");
        }
    }
}
