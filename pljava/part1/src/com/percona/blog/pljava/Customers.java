package com.percona.blog.pljava;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Customers {
	private static String m_url = "jdbc:default:connection";

	public static String getCustomerInfo(Integer id) throws SQLException {
		Connection conn = DriverManager.getConnection(m_url);
		String query = "SELECT c.customer_id, c.last_name ||', '|| c.first_name as full_name, "
				+ " c.email, a.address, ci.city, a.district "
				+ " FROM customer c"
				+ "	 JOIN address a on a.address_id = c.address_id "
				+ "	JOIN city ci on ci.city_id = a.city_id "
				+ " WHERE customer_id = ?";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setInt(1, id);
		ResultSet rs = stmt.executeQuery();
		rs.next();
		String ret; 
		ret = "- ID: " + rs.getString("customer_id") ;
		ret += "\n- Name: " + rs.getString("full_name");
		ret += "\n- Email: " + rs.getString("email");
		ret += "\n- Address: " + rs.getString("address");
		ret += "\n- City: " + rs.getString("city");
		ret += "\n- District: " + rs.getString("district");
		ret += "\n--------------------------------------------------------------------------------";

		stmt.close();
		conn.close();

		return (ret);
	}

	public static String getCustomerTotal(Integer id) throws SQLException {
		Connection conn;
		PreparedStatement stmt;
		ResultSet rs;
		String result;
		double total;

		conn = DriverManager.getConnection(m_url);
		stmt = conn.prepareStatement(
				"SELECT c.customer_id, c.first_name, c.last_name FROM customer c WHERE c.customer_id = ?");
		stmt.setInt(1, id);
		rs = stmt.executeQuery();
		if (rs.next()) {
			result = "Customer ID  : " + rs.getInt("customer_id");
			result += "\nCustomer Name: " + rs.getString("last_name") + ", " + rs.getString("first_name");
			result += "\n--------------------------------------------------------------------------------------------------------";
		} else {
			return null;
		}

		stmt = conn.prepareStatement("SELECT p.payment_date, p.amount FROM payment p WHERE p.customer_id = ? ORDER BY 1");
		stmt.setInt(1, id);
		rs = stmt.executeQuery();
		total = 0;

		while (rs.next()) {
			result += "\nPayment date: " + rs.getString("payment_date") + ",    Value: " + rs.getString("amount");
			total += rs.getFloat("amount");
		}
		result += "\n--------------------------------------------------------------------------------------------------------";
		result += "\nTotal: " +String.format("%1$,.2f",  total);
		
		stmt.close();
		conn.close();
		return (result);
	}
	
	

}
