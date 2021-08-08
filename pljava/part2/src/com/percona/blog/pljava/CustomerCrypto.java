package com.percona.blog.pljava;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.TriggerData;

public class CustomerCrypto implements ResultSetProvider {
	private final String m_url = "jdbc:default:connection";
	private final Connection conn;
	private PreparedStatement stmt;
	private ResultSet rs;

	//
	private PrivateKey privateKey;
	private PublicKey publicKey;

	public CustomerCrypto() throws SQLException, NoSuchAlgorithmException {
		String query;

		query = "SELECT * FROM keys WHERE id = 1";
		conn = DriverManager.getConnection(m_url);
		stmt = conn.prepareStatement(query);
		rs = stmt.executeQuery();
		if (!rs.next())
			throw new SQLException("Keys not found!");

		privateKey = Crypto.getPrivateKey(rs.getString("priv"));
		publicKey = Crypto.getPublicKey(rs.getString("pub"));
	}
	
	public void processQuery(int id) throws SQLException, NoSuchAlgorithmException {
		String query;
		query = "SELECT * FROM customer2 WHERE customer_id = ?";
		stmt = conn.prepareStatement(query);
		stmt.setInt(1, id);
		rs = stmt.executeQuery();
	}

	@Override
	public void close() throws SQLException {
		stmt.close();
		conn.close();
	}

	public static int getLineNumber() {
		return Thread.currentThread().getStackTrace()[2].getLineNumber();
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow) throws SQLException {
		if (!rs.next())
			return false;

		try {
			receiver.updateInt(1, rs.getInt("customer_id"));
			receiver.updateInt(2, rs.getInt("store_id"));
			receiver.updateString(3, Crypto.decrypt(rs.getString("first_name"), this.privateKey));
			receiver.updateString(4, Crypto.decrypt(rs.getString("last_name"), this.privateKey));
			receiver.updateString(5, Crypto.decrypt(rs.getString("email"), this.privateKey));
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
	
	private void encryptData(TriggerData td) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, SQLException {
		ResultSet _new = td.getNew();
		
		_new.updateString("first_name", Crypto.encrypt(_new.getString("first_name"), this.publicKey));
		_new.updateString("last_name", Crypto.encrypt(_new.getString("last_name"), this.publicKey));
		_new.updateString("email", Crypto.encrypt(_new.getString("email"), this.publicKey));
	}
	
	public static void customerBeforeInsertUpdate(TriggerData td) throws SQLException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException {
		CustomerCrypto ret = new CustomerCrypto();
		ret.encryptData(td);
	}

	public static ResultSetProvider getCustomerCrypto(int id) throws SQLException, NoSuchAlgorithmException {
		CustomerCrypto ret = new CustomerCrypto();
		ret.processQuery(id);
		
		return ret;
	}

}
