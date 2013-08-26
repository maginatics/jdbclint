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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Test JDBC lint checks. */
// TODO: test close/finalize methods
public final class JdbcLintTest {
    private static AtomicLong dbNumber = new AtomicLong();
    private DataSource dataSource;

    /** Helper to match arbitrary exceptions in tests. */
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    /** Create database with a single-column table. */
    @Before
    public void setUp() throws SQLException {
        dataSource = getDataSource();
        Connection conn = dataSource.getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE table (column INT)");
            try {
                stmt.execute();
            } finally {
                stmt.close();
            }
        } finally {
            conn.close();
        }
    }

    @Test
    public void testConnectionDoubleClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();
        conn.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Connection already closed");
        conn.close();
    }

    @Test
    public void testConnectionMissingCommitOrRollback() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Connection did not commit or roll back");
        conn.close();
    }

    @Test
    public void testConnectionMissingPrepareStatement() throws SQLException {
        Connection conn = dataSource.getConnection();
        thrown.expect(SQLException.class);
        thrown.expectMessage("Connection without prepareStatement");
        conn.close();
    }

    @Test
    public void testPreparedStatementDoubleClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement already closed");
        stmt.close();
    }

    @Test
    public void testPreparedStatementMissingExecute() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement without execute");
        stmt.close();
    }

    @Test
    public void testPreparedStatementMissingExecuteBatch() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.addBatch();

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement addBatch without executeBatch");
        stmt.close();
    }

    @Test
    public void testResultSetAllRowsConsumed() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();
        stmt = conn.prepareStatement("SELECT * FROM table");
        ResultSet rs = stmt.executeQuery();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet not fully consumed");
        rs.close();
    }

    @Test
    public void testResultSetDoubleClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM table");
        ResultSet rs = stmt.executeQuery();
        rs.next();
        rs.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet already closed");
        rs.close();
    }

    @Test
    public void testResultSetUnreadColumn() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO table (column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();
        stmt = conn.prepareStatement("SELECT * FROM table");
        ResultSet rs = stmt.executeQuery();
        rs.next();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet has unread column: column");
        rs.next();
    }

    private static DataSource getDataSource() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty(JdbcLint.FAIL_METHOD, "throw_sql_exception");

        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        String dbName = "database-" + dbNumber.addAndGet(1);
        jdbcDataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        return DataSourceProxy.newInstance(jdbcDataSource, properties);
    }
}
