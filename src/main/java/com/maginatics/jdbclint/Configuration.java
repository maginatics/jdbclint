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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for JDBC lint.  Most users should use
 * Configuration.DEFAULT_CHECKS which provides sane defaults.
 */
public final class Configuration {
    /** JDBC lint checks. */
    public enum Check {
        BLOB_DOUBLE_FREE,
        BLOB_MISSING_FREE,
        CONNECTION_DOUBLE_CLOSE,
        CONNECTION_MISSING_CLOSE,
        CONNECTION_MISSING_COMMIT_OR_ROLLBACK,
        CONNECTION_MISSING_PREPARE_STATEMENT,
        CONNECTION_MISSING_READ_ONLY,
        PREPARED_STATEMENT_DOUBLE_CLOSE,
        PREPARED_STATEMENT_MISSING_CLOSE,
        PREPARED_STATEMENT_MISSING_EXECUTE,
        PREPARED_STATEMENT_MISSING_EXECUTE_BATCH,
        RESULT_SET_DOUBLE_CLOSE,
        RESULT_SET_MISSING_CLOSE,
        RESULT_SET_UNREAD_COLUMN,
        STATEMENT_DOUBLE_CLOSE,
        STATEMENT_MISSING_CLOSE,
        STATEMENT_MISSING_EXECUTE,
        STATEMENT_MISSING_EXECUTE_BATCH;
    }
    private final Set<Check> checks;

    public static final Set<Check> DEFAULT_CHECKS =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(
                    Check.CONNECTION_MISSING_READ_ONLY)));

    /** Action to take after failing a check. */
    public interface Action {
        void apply(String message, Exception exception) throws SQLException;
    }
    private final Collection<Action> actions;

    public static final Action PRINT_STACK_TRACE_ACTION = new Action() {
        @Override
        public void apply(final String message, final Exception exception) {
            new SQLException(exception).printStackTrace();
        }
    };

    public static final Action SYSTEM_EXIT_ACTION = new Action() {
        @Override
        public void apply(final String message, final Exception exception) {
            System.exit(1);
        }
    };

    public static final Action THROW_RUNTIME_EXCEPTION_ACTION = new Action() {
        @Override
        public void apply(final String message, final Exception exception) {
            throw new RuntimeException(message, exception);
        }
    };

    public static final Action THROW_SQL_EXCEPTION_ACTION = new Action() {
        @Override
        public void apply(final String message, final Exception exception)
                throws SQLException {
            throw new SQLException(message, exception);
        }
    };

    public static Action printStackTraceToFile(final File file) {
        Utils.checkNotNull(file);
        return new Action() {
            @Override
            public void apply(final String message, final Exception exception) {
                PrintStream ps = null;
                try {
                    ps = new PrintStream(new FileOutputStream(file),
                            /*append=*/ true);
                    ps.println(message);
                    new SQLException(exception).printStackTrace(ps);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            }
        };
    }

    public Configuration(final Set<Check> checks,
            final Collection<Action> actions) {
        this.checks = Collections.unmodifiableSet(EnumSet.copyOf(
                Utils.checkNotNull(checks)));
        this.actions = Collections.unmodifiableCollection(
                new ArrayList<Action>(Utils.checkNotNull(actions)));
    }

    public boolean isEnabled(final Check check) {
        return checks.contains(Utils.checkNotNull(check));
    }

    public Set<Check> getChecks() {
        return checks;
    }

    public Collection<Action> getActions() {
        return actions;
    }
}
