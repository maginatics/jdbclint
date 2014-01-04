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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for JDBC lint.  Most users should use
 * Configuration.DEFAULT_CHECKS which provides sane defaults.
 */
public final class Configuration {
    /** Method to handle failures. */
    public enum FailMethod {
        EXIT,
        NO_OPERATION,
        THROW_RUNTIME_EXCEPTION,
        THROW_SQL_EXCEPTION;
    }
    private final FailMethod failMethod;

    /** Location to log warnings to.  If null, log to stderr. */
    private final String logFile;

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

    private Configuration(final FailMethod failMethod,
            final String logFile, final Set<Check> checks) {
        this.failMethod = Utils.checkNotNull(failMethod);
        this.logFile = logFile;
        this.checks = EnumSet.copyOf(Utils.checkNotNull(checks));
    }

    public static final Set<Check> DEFAULT_CHECKS =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(
                    Check.CONNECTION_MISSING_READ_ONLY)));

    public static Builder builder() {
        return new Builder();
    }

    public FailMethod getFailMethod() {
        return failMethod;
    }

    public String getLogFile() {
        return logFile;
    }

    public boolean isEnabled(final Check check) {
        return checks.contains(Utils.checkNotNull(check));
    }

    /** Configuration builder for JDBC lint. */
    public static final class Builder {
        private FailMethod failMethod = FailMethod.NO_OPERATION;
        private String logFile = null;
        private Set<Check> checks = DEFAULT_CHECKS;

        private Builder() {
        }

        public Builder setFailMethod(final FailMethod value) {
            this.failMethod = Utils.checkNotNull(value);
            return this;
        }

        public Builder setLogFile(final String value) {
            this.logFile = value;
            return this;
        }

        public Builder setChecks(final Set<Check> value) {
            this.checks = Utils.checkNotNull(value);
            return this;
        }

        public Configuration build() {
            return new Configuration(failMethod, logFile, checks);
        }
    }
}
