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
import java.util.Properties;

/** JdbcLint finds misuse of the JDBC API. */
public final class JdbcLint {
    /**
     * Method to handle failures.  If unset, do nothing.  Valid values include
     * exit and throw_runtime_exception.
     */
    public static final String FAIL_METHOD =
            "com.maginatics.jdbclint.fail_method";
    /** Location to log warnings to.  If not set, logged to stderr. */
    public static final String LOG_FILE =
            "com.maginatics.jdbclint.log_file";
    public static final String CONNECTION_DOUBLE_CLOSE =
            "com.maginatics.jdbclint.connection.double_close";
    public static final String CONNECTION_MISSING_CLOSE =
            "com.maginatics.jdbclint.connection.missing_close";
    public static final String CONNECTION_MISSING_COMMIT_OR_ROLLBACK =
            "com.maginatics.jdbclint.connection.missing_commit_or_rollback";
    public static final String CONNECTION_MISSING_PREPARE_STATEMENT =
            "com.maginatics.jdbclint.connection.missing_prepare_statement";
    public static final String PREPARED_STATEMENT_DOUBLE_CLOSE =
            "com.maginatics.jdbclint.preparedstatement.double_close";
    public static final String PREPARED_STATEMENT_MISSING_CLOSE =
            "com.maginatics.jdbclint.preparedstatement.missing_close";
    public static final String PREPARED_STATEMENT_MISSING_EXECUTE =
            "com.maginatics.jdbclint.preparedstatement.missing_execute";
    public static final String PREPARED_STATEMENT_MISSING_EXECUTE_BATCH =
            "com.maginatics.jdbclint.preparedstatement.missing_execute_batch";
    public static final String RESULT_SET_ALL_ROWS_CONSUMED =
            "com.maginatics.jdbclint.resultset.all_rows_consumed";
    public static final String RESULT_SET_DOUBLE_CLOSE =
            "com.maginatics.jdbclint.resultset.double_close";
    public static final String RESULT_SET_MISSING_CLOSE =
            "com.maginatics.jdbclint.resultset.missing_close";
    public static final String RESULT_SET_UNREAD_COLUMN =
            "com.maginatics.jdbclint.resultset.unread_column";
    public static final String BLOB_DOUBLE_FREE =
            "com.maginatics.jdbclint.blob.double_free";
    public static final String BLOB_MISSING_FREE =
            "com.maginatics.jdbclint.blob.missing_free";

    private JdbcLint() {
        // intentionally unimplemented
    }

    static void fail(final Properties properties, final Exception exception,
            final String message) throws SQLException {
        // log warnings
        String logFile = properties.getProperty(LOG_FILE);
        PrintStream ps = null;
        try {
            if (logFile == null) {
                ps = System.err;
            } else {
                ps = new PrintStream(new FileOutputStream(new File(logFile),
                        /*append=*/ true));
            }
            ps.println(message);
            exception.printStackTrace(ps);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            if (logFile != null && ps != null) {
                ps.close();
            }
        }

        // handle failures
        String failMethod = properties.getProperty(FAIL_METHOD);
        if (failMethod == null) {
            return;
        } else if (failMethod.equals("exit")) {
            System.exit(1);
        } else if (failMethod.equals("throw_runtime_exception")) {
            throw new RuntimeException(message, exception);
        } else if (failMethod.equals("throw_sql_exception")) {
            throw new SQLException(message, exception);
        }
    }

    static boolean nullEmptyOrTrue(final String value) {
        return value == null || value.isEmpty() ||
                value.equalsIgnoreCase("true");
    }

    static <T> T checkNotNull(final T obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }
}
