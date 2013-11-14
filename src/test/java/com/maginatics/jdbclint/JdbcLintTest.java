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

import java.lang.reflect.Proxy;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Test JDBC lint checks. */
public final class JdbcLintTest {
    private static final Configuration config = Configuration.defaults()
            .setFailMethod(Configuration.FailMethod.THROW_SQL_EXCEPTION)
            .build();
    private static final String DATABASE_NAME = "jdbclinttest";
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
            Statement stmt = conn.createStatement();
            try {
                stmt.execute("DROP TABLE IF EXISTS blob_table");
                stmt.execute("DROP TABLE IF EXISTS int_table");
                stmt.execute("CREATE TABLE int_table (int_column INT)");
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
                "INSERT INTO int_table (int_column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();
        conn.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Connection already closed");
        conn.close();
    }

    @Test
    public void testConnectionMissingClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        ConnectionProxy proxy = (ConnectionProxy) Proxy.getInvocationHandler(
                conn);

        thrown.expect(SQLException.class);
        thrown.expectMessage("Connection not closed");
        proxy.finalize();
    }

    @Test
    public void testConnectionMissingCommitOrRollback() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO int_table (int_column) VALUES (?)");
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
                "INSERT INTO int_table (int_column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement already closed");
        stmt.close();
    }

    @Test
    public void testPreparedStatementMissingClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO int_table (int_column) VALUES (?)");
        StatementProxy proxy = (StatementProxy) Proxy.getInvocationHandler(
                stmt);
        stmt.setInt(1, 0);
        stmt.executeUpdate();

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement not closed");
        proxy.finalize();
    }

    @Test
    public void testPreparedStatementMissingExecute() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO int_table (int_column) VALUES (?)");
        stmt.setInt(1, 0);

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement without execute");
        stmt.close();
    }

    @Test
    public void testPreparedStatementMissingExecuteBatch() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO int_table (int_column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.addBatch();

        thrown.expect(SQLException.class);
        thrown.expectMessage("PreparedStatement addBatch without executeBatch");
        stmt.close();
    }

    @Test
    public void testResultSetDoubleClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM int_table");
        ResultSet rs = stmt.executeQuery();
        rs.next();
        rs.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet already closed");
        rs.close();
    }

    @Test
    public void testResultSetMissingClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM int_table");
        ResultSet rs = stmt.executeQuery();
        ResultSetProxy proxy = (ResultSetProxy) Proxy.getInvocationHandler(rs);
        rs.next();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet not closed");
        proxy.finalize();
    }

    @Test
    public void testResultSetUnreadColumn() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO int_table (int_column) VALUES (?)");
        stmt.setInt(1, 0);
        stmt.executeUpdate();
        stmt.close();
        stmt = conn.prepareStatement("SELECT * FROM int_table");
        ResultSet rs = stmt.executeQuery();
        rs.next();

        thrown.expect(SQLException.class);
        thrown.expectMessage("ResultSet has unread column: int_column");
        rs.next();
    }

    @Test
    public void testStatementDoubleClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("INSERT INTO int_table (int_column) VALUES (0)");
        stmt.close();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Statement already closed");
        stmt.close();
    }

    @Test
    public void testStatementMissingClose() throws SQLException {
        Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        StatementProxy proxy = (StatementProxy) Proxy.getInvocationHandler(
                stmt);
        stmt.executeUpdate("INSERT INTO int_table (int_column) VALUES (0)");

        thrown.expect(SQLException.class);
        thrown.expectMessage("Statement not closed");
        proxy.finalize();
    }

    @Test
    public void testStatementMissingExecute() throws SQLException {
        Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Statement without execute");
        stmt.close();
    }

    @Test
    public void testStatementMissingExecuteBatch() throws SQLException {
        Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        stmt.addBatch("INSERT INTO int_table (int_column) VALUES (0)");

        thrown.expect(SQLException.class);
        thrown.expectMessage("Statement addBatch without executeBatch");
        stmt.close();
    }

    @Test
    public void testBlobDoubleFree() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "CREATE TABLE blob_table (blob_column BLOB)");
        stmt.executeUpdate();
        stmt.close();

        stmt = conn.prepareStatement(
                "INSERT INTO blob_table (blob_column) VALUES (?)");
        stmt.setBytes(1, new byte[1]);
        stmt.executeUpdate();
        stmt.close();

        stmt = conn.prepareStatement("SELECT blob_column FROM blob_table");
        ResultSet rs = stmt.executeQuery();
        rs.next();

        Blob blob = rs.getBlob("blob_column");
        blob.free();

        thrown.expect(SQLException.class);
        thrown.expectMessage("Blob already freed");
        blob.free();
    }

    @Test
    public void testBlobMissingFree() throws SQLException {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
                "CREATE TABLE blob_table (blob_column BLOB)");
        stmt.executeUpdate();
        stmt.close();

        stmt = conn.prepareStatement(
                "INSERT INTO blob_table (blob_column) VALUES (?)");
        stmt.setBytes(1, new byte[1]);
        stmt.executeUpdate();
        stmt.close();

        stmt = conn.prepareStatement("SELECT blob_column FROM blob_table");
        ResultSet rs = stmt.executeQuery();
        rs.next();

        Blob blob = rs.getBlob("blob_column");
        BlobProxy proxy = (BlobProxy) Proxy.getInvocationHandler(blob);

        thrown.expect(SQLException.class);
        thrown.expectMessage("Blob not freed");
        proxy.finalize();
    }

    private static DataSource getDataSource() {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:" + DATABASE_NAME +
                ";DB_CLOSE_DELAY=-1");
        return DataSourceProxy.newInstance(jdbcDataSource, config);
    }
}
