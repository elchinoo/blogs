package com.percona.blog.pljava;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;

import org.postgresql.pljava.annotation.Function;

public class CustomerResultSet implements ResultSetHandle {
	private Connection conn;
	private PreparedStatement stmt;
	private final String m_url = "jdbc:default:connection";
	private final String sql = "select * FROM customer LIMIT 10";

	public CustomerResultSet() throws SQLException {
		conn = DriverManager.getConnection(m_url);
		stmt = conn.prepareStatement(sql);
	}

	@Override
	public void close() throws SQLException {
		stmt.close();
		conn.close();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return stmt.executeQuery();
	}

	@Function(type = "customer")
	public static ResultSetHandle getCustomerLimit10() throws SQLException {
		return new CustomerResultSet();
	}
}
