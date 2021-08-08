package com.percona.blog.pljava;

import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.pljava.ResultSetProvider;

public class CustomerHash implements ResultSetProvider {
	private final Connection conn;
	private final PreparedStatement stmt;
	private final ResultSet rs;
	private final Crypto crypto;
	
	private final String m_url = "jdbc:default:connection";

	public CustomerHash(int id) throws SQLException, NoSuchAlgorithmException {
		String query;
		
		crypto = new Crypto();
		query = "SELECT * FROM customer WHERE customer_id = ?";
		conn = DriverManager.getConnection(m_url);
		stmt = conn.prepareStatement(query);
		stmt.setInt(1, id);
		rs = stmt.executeQuery();
	}
	
	@Override
	public void close() throws SQLException {
		stmt.close();
		conn.close();
	}
	
	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow) throws SQLException {		
		if (!rs.next())
			return false;
		
		try {
			receiver.updateInt(1, rs.getInt("customer_id"));
			receiver.updateInt(2, rs.getInt("store_id"));
			receiver.updateString(3, crypto.encode(rs.getString("first_name"), 5, 45));
			receiver.updateString(4, crypto.encode(rs.getString("last_name"), 5, 45));
			receiver.updateString(5, crypto.encode(rs.getString("email"), 5, 41) + "@mail.com");
			receiver.updateInt(6, rs.getInt("address_id"));
			receiver.updateBoolean(7, rs.getBoolean("activebool"));
			receiver.updateDate(8, rs.getDate("create_date"));
			receiver.updateTimestamp(9, rs.getTimestamp("last_update"));
			receiver.updateInt(10, rs.getInt("active"));
			
		} catch (Exception e) {
			Logger.getAnonymousLogger().log(Level.parse("SEVERE"), e.getMessage());
		}
		return true;
	}
	
	public static ResultSetProvider getCustomerAnonymized(int id) throws SQLException, NoSuchAlgorithmException {
		return new CustomerHash(id);
	}

}
