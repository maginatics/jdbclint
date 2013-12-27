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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;

/** Utility methods. */
final class Utils {
    private Utils() {
        // intentionally unimplemented
    }

    static void fail(final Configuration config, final Exception exception,
            final String message) throws SQLException {
        // log warnings
        String logFile = config.getLogFile();
        PrintStream ps = null;
        try {
            if (logFile == null) {
                ps = System.err;
            } else {
                ps = new PrintStream(new FileOutputStream(new File(logFile),
                        /*append=*/ true));
            }
            ps.println(message);
            new SQLException(exception).printStackTrace(ps);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            if (logFile != null && ps != null) {
                ps.close();
            }
        }

        // handle failures
        switch (config.getFailMethod()) {
        case EXIT:
            System.exit(1);
        case THROW_RUNTIME_EXCEPTION:
            throw new RuntimeException(message, exception);
        case THROW_SQL_EXCEPTION:
            throw new SQLException(message, exception);
        case NO_OPERATION:
        default:
            break;
        }
    }

    static <T> T checkNotNull(final T obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }
}
