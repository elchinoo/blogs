# PostgreSQL PL/Java – A How-To, Part 2

We discussed how to install and created a simple class in the first part of this series where we ran a SELECT and returned one row with one single column with a formatted text. Now it's time to expand and see how to return multiple tuples. 

A little disclaimer here, I'm not going to comment much on the Java code as this is not intended to be a Java tutorial and the examples here are just of educational purpose, not intended to be of high performance neither used in production!

## Returning a table structure
This first example will show how we can select and return a table from a PL/Java function. We'll keep using the table "**customer**" here and the probing SQL will be:
```sql
SELECT * FROM customer LIMIT 10;
```

I will create a new Java class here called "**CustomerResultSet**" and the initial code is:
```java
package com.percona.blog.pljava;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;

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

	public static ResultSetHandle getCustomerPayments() throws SQLException {
		return new CustomerResultSet();
	}
}
```

Note that we are implementing the **org.postgresql.pljava.ResultSetHandle** interface provided by PL/Java. We need it because we are returning a complex object and the ResultSetHandle interface is appropriated when we won't manipulate the returned tuples.

Now that we are using PL/Java objects we need to tell the compiler where to find those references and for this first example here we need the **pljava-api jar**, which in my case happens to be **pljava-api-1.6.2.jar**. If you remember from the first post I've compiled the PL/Java I'm using here and my jar is located at "**~/pljava-1_6_2/pljava-api/target/pljava-api-1.6.2.jar**" and the compilation command will be:
```bash
javac -cp "~/pljava-1_6_2/pljava-api/target/pljava-api-1.6.2.jar" com/percona/blog/pljava/CustomerResultSet.java
jar -c -f /app/pg12/lib/pljavaPart2.jar com/percona/blog/pljava/CustomerResultSet.class
```

With my new JAR file created I can then install it into Postgres and create the function "**getCustomerLimit10()**":
```sql
SELECT sqlj.install_jar( 'file:///app/pg12/lib/pljavaPart2.jar', 'pljavaPart2', true );
SELECT sqlj.set_classpath( 'public', 'pljavaPart2' );
CREATE OR REPLACE FUNCTION getCustomerLimit10() RETURNS SETOF customer AS 'com.percona.blog.pljava.CustomerResultSet.getCustomerLimit10' LANGUAGE java;
```

The result of the function call is:
```sql
test=# SELECT * FROM getCustomerLimit10();
 customer_id | store_id | first_name | last_name |                email                | address_id | activebool | create_date |     last_update     | active 
-------------+----------+------------+-----------+-------------------------------------+------------+------------+-------------+---------------------+--------
           1 |        1 | MARY       | SMITH     | MARY.SMITH@sakilacustomer.org       |          5 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           2 |        1 | PATRICIA   | JOHNSON   | PATRICIA.JOHNSON@sakilacustomer.org |          6 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           3 |        1 | LINDA      | WILLIAMS  | LINDA.WILLIAMS@sakilacustomer.org   |          7 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           4 |        2 | BARBARA    | JONES     | BARBARA.JONES@sakilacustomer.org    |          8 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           5 |        1 | ELIZABETH  | BROWN     | ELIZABETH.BROWN@sakilacustomer.org  |          9 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           6 |        2 | JENNIFER   | DAVIS     | JENNIFER.DAVIS@sakilacustomer.org   |         10 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           7 |        1 | MARIA      | MILLER    | MARIA.MILLER@sakilacustomer.org     |         11 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           8 |        2 | SUSAN      | WILSON    | SUSAN.WILSON@sakilacustomer.org     |         12 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           9 |        2 | MARGARET   | MOORE     | MARGARET.MOORE@sakilacustomer.org   |         13 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
          10 |        1 | DOROTHY    | TAYLOR    | DOROTHY.TAYLOR@sakilacustomer.org   |         14 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
(10 rows)

test=# 
```

## Manipulating the result before return
Return the result of a plain SQL has its usage like visibility/permissioning control but we usually need to manipulate the results of a query before return and to do this we can implement the interface "**org.postgresql.pljava.ResultSetProvider**". 

The below example I will implement a simple method to anonymize sensitive data with a hash function. I will create a helper class to deal with the hash and cryptographic functions to keep the CustomerResultSet class clean:
```java
/**
 * Crypto helper class that will contain all hashing and cryptographic functions
 */
package com.percona.blog.pljava;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Crypto {
	MessageDigest digest;
	
	public Crypto() throws NoSuchAlgorithmException {
		digest = MessageDigest.getInstance("SHA-256");
	}
	
	public String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);
		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
	
	public String encode(String data, int min, int max) {
		double salt = Math.random();
		int sbstring = (int) ((Math.random() * ((max - min) + 1)) + min);

		return bytesToHex(digest.digest((data + salt).getBytes(StandardCharsets.UTF_8))).substring(0, sbstring);
	}
}

/**
 * CustomerHash class
 */
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
```

The number of classes are increasing, then instead of mentioning them one by one lets just use the "*.java" to build the classes and the "*.class" to create the jar:
```bash
javac -cp "~/pljava-1_6_2/build/pljava-api-1.6.2.jar" com/percona/blog/pljava/*.java
jar -c -f /app/pg12/lib/pljavaPart2.jar com/percona/blog/pljava/*.class
```

Remember that every time we change our JAR file we need to reload it from Postgres. We'll do it below and also create and test our new function/method:
```sql
test=# SELECT sqlj.replace_jar( 'file:///app/pg12/lib/pljavaPart2.jar', 'pljavaPart2', true );
 replace_jar 
-------------
 
(1 row)

test=# CREATE OR REPLACE FUNCTION getCustomerAnonymized(int) RETURNS SETOF customer AS 'com.percona.blog.pljava.CustomerHash.getCustomerAnonymized' LANGUAGE java;
CREATE FUNCTION

test=# SELECT * FROM getCustomerAnonymized(9);
 customer_id | store_id |     first_name      |              last_name              |                  email                  | address_id | activebool | create_date |     last_update     | ac
tive 
-------------+----------+---------------------+-------------------------------------+-----------------------------------------+------------+------------+-------------+---------------------+---
-----
           9 |        2 | 72e2616ef0075e81929 | 3559c00ee546ae0062460c8faa4f24960f1 | 24854ed40ed42b57f077cb1cfaf916@mail.com |         13 | t          | 2006-02-14  | 2006-02-15 09:57:20 |   
   1
(1 row)

test=# 
```

Great! We now have a method to anonymize data!

## Triggers
The last topic of this second part will be triggers and to make it a bit interesting we will create a trigger to encrypt the sensitive data of our table. The anonymization using hash function in the previous example is great but what happens if we have unauthorized access to the database? The data is saved in plain text!

To make this example as smaller as possible I won't bother with securing the keys, we will do it in the part 3 of this series when we will use Java to access external resources, in our case we'll use Vault to secure our keys, so keep tunned!

Ok, the first thing we need to do is to create the pair of keys we need to encrypt/decrypt our data. I will use "**openssl**" to create them and will store them into a table named "keys"!
```bash
openssl genrsa -out keypair.pem 2048
openssl pkcs8 -topk8 -nocrypt -in keypair.pem -outform PEM -out private.pem
openssl rsa -in keypair.pem -outform PEM -pubout -out public.pem
```

Now that we have the keys we need to sanityze the data to remove the header and footer data from both the private and public keys and also remove all break-lines or else our Java code will complain:
```bash
echo -n "CREATE TABLE keys(id int primary key, priv varchar not null, pub varchar not null); INSERT INTO keys VALUES(1, '" > keys.sql
cat private.pem | sed '1d;$d' | sed ':a;N;$!ba;s/\n//g' | tr -d '\n' >> keys.sql
echo -n "', '" >> keys.sql
cat public.pem | sed '1d;$d' | sed ':a;N;$!ba;s/\n//g' | tr -d '\n' >> keys.sql
echo -n "');" >> keys.sql

psql test < keys.sql
```

It will look like something like this when sanitized:
```sql
CREATE TABLE keys(id int primary key, priv varchar not null, pub varchar not null); INSERT INTO keys VALUES(1, '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');
```

After done with populating the table we should have a nice table with both private and public key. Now is time to create our Java classes. I will reuse the "**Crypto**" class for the cryptographic functions and will create a new class to add our trigger functions. I will only add the relevant part of the Crypto class here but you can find the code described here in my [github page here[1]](https://github.com/elchinoo/blogs/tree/main/pljava) including Part 1 and Part 3 when released. Let's get to the code:
```java
/**
 * This is the relevant part of the Crypto class that will encrypt and decrypt our data using the certificates we generated above.
 */
	public PublicKey getPublicKey(String base64PublicKey) {
		PublicKey publicKey = null;
		try {
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.getBytes()));
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			publicKey = keyFactory.generatePublic(keySpec);
			return publicKey;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		}
		return publicKey;
	}

	public PrivateKey getPrivateKey(String base64PrivateKey) {
		PrivateKey privateKey = null;
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64PrivateKey.getBytes()));
		KeyFactory keyFactory = null;
		try {
			keyFactory = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		try {
			privateKey = keyFactory.generatePrivate(keySpec);
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		}
		return privateKey;
	}

	public String encrypt(String data, PublicKey publicKey) throws BadPaddingException, IllegalBlockSizeException,
			InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
	}
	
	public String decrypt(String data, PrivateKey privateKey) throws NoSuchPaddingException,
			NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		return new String(cipher.doFinal(Base64.getDecoder().decode(data)));
	}
```

Now we can implement the class with both our trigger function to encrypt and also a function to decrypt when we need to SELECT data:
```java
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
		query = "SELECT * FROM customer WHERE customer_id = ?";
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
```

The relevant parts of the code above are the "**customerBeforeInsertUpdate**" and "**encryptData**" methods. The former is the static method the database will access. The PL/Java on Postgres expects to find a static method with "**void (TriggerData)**" signature. It will then call the "encryptData" method of the CustomerCrypto object to do the job. The "encryptData" method then recovers the resultset from the "**NEW**" pointer that is passed through the TriggerData object and changes the value to crypt the data. We need to call the trigger in the BEFORE event because we need to crypt it before it is persisted.

Another important method is the "**getCustomerCrypto**". We need to be able to get the data decrypted and this method will help us. We use here the same technique we used in the previous example where we implement the "**ResultSetProvider**" interface and manipulate the data before returning the resultset. Take a closer look to the "**assignRowValues**" method and you'll see that we are decrypting the data there with "**Crypto.decrypt**" method!

Ok, time to compile the code and check if it really works:
```bash
javac -cp "/v01/proj/percona/blog/pljava/pljava-1_6_2/build/pljava-api-1.6.2.jar" com/percona/blog/pljava/*.java
jar -c -f /app/pg12/lib/pljavaPart2.jar com/percona/blog/pljava/*.class
```

And create the database objects:
```sql
SELECT sqlj.replace_jar( 'file:///app/pg12/lib/pljavaPart2.jar', 'pljavaPart2', true );

CREATE FUNCTION customerBeforeInsertUpdate()
			RETURNS trigger
			AS 'com.percona.blog.pljava.CustomerCrypto.customerBeforeInsertUpdate'
			LANGUAGE java;

CREATE TRIGGER tg_customerBeforeInsertUpdate
			BEFORE INSERT ON customer
			FOR EACH ROW
			EXECUTE PROCEDURE customerBeforeInsertUpdate();
```

At this point our data isn't encrypted yet but we can do it with a noop update and the trigger will do its magic:
```sql
test=# SELECT * FROM customer LIMIT 5;
 customer_id | store_id | first_name | last_name |                email                | address_id | activebool | create_date |     last_update     | active 
-------------+----------+------------+-----------+-------------------------------------+------------+------------+-------------+---------------------+--------
           1 |        1 | MARY       | SMITH     | MARY.SMITH@sakilacustomer.org       |          5 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           2 |        1 | PATRICIA   | JOHNSON   | PATRICIA.JOHNSON@sakilacustomer.org |          6 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           3 |        1 | LINDA      | WILLIAMS  | LINDA.WILLIAMS@sakilacustomer.org   |          7 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           4 |        2 | BARBARA    | JONES     | BARBARA.JONES@sakilacustomer.org    |          8 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
           5 |        1 | ELIZABETH  | BROWN     | ELIZABETH.BROWN@sakilacustomer.org  |          9 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
(5 rows)

test=# UPDATE customer SET first_name = first_name, last_name = last_name, email = email;
UPDATE 599

test=# SELECT * FROM customer LIMIT 5;
 customer_id | store_id |                                                                                                                                                                       
 first_name                                                                                                                                                                        |            
                                                                                                                                                            last_name                           
                                                                                                                                              |                                                 
                                                                                                                         email                                                                  
                                                                                                         | address_id | activebool | create_date |        last_update         | active 
-------------+----------+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
----------------------------------------------------------------------------------------------------------------------------------------------+-------------------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------+------------+------------+-------------+----------------------------+--------
           3 |        1 | DT8oXb0VQvtFSIOv61zZfmUrpjWWGoeyl8D8tQl7naCLT31WlJn3U+uILYqUedSOdDSO17QdQKwChWG+DrcvYEih9RYyEPR2ja9deN4cn+vwHt/v09HDfmwrsJUt0UgP/fp78hCxkJDAV50KkMUsA23aeH5HRn9nCHOH0P
AcuId+7acCgwvU9YP8Sx2KVeVnLaBrzpeRLrsmAczQLUAXilXfdFC8uT2APBfwx1So2eCe+kSOsjcu1yTwlsa95Dnfu/N++Zm1D4knKUrAuNm5svTHjIz+B4HKXFMPz/Yk7KYF6ThB6OshIomyRvSEtKu5torfdwAvT3tsgP2DLWiKgQ== | H0YRoi10z36
tnNSXpBs/oYfMQRbAhfUYLIcE885Dhxmy2mbuhecCCqPcye5/++MhUwmEQG2pBgfsqWHLOnAgbqjaG3O0reipVysYK7cMysX1w5RVINsyD5H3vCqgnHESfdRuhW3b00InkR2qCtBYX1QJ1tKJZz89D2AOjoTq5jTum00vcLT06h6ZxVh1RKLNAuGpY9qN57m
/9a4JZuff9poYjw2PPQ6kTxhtbFl3bw+B3sJUcLFuFMYUoAAHsVETQRAerH1ncG9Uxi+xQjUtTVBqZdjvED+eydetH7vsnjBuYDtXD9XAn14qmALx5NfvwpU5jfpMOPOM4xP1BRVA2Q== | DpWBfhhii4LRPxZ9XJy8xoNne+qm051wD5Gd9AMHc+oIhx/B
ln6H+lcAM3625rKN1Vw/lG6VkQo2EnoZz/bhFtULvAOAUiBxerBDbYe0lYWqI50NxnFJbkexMkjDSiuoglh3ilRBn6Z+WGLc7FfEprOd1+tULW2gcwLI68uoEhfSY7INQZuGXfOUMAM4roB2fWvEfylL1ShbiGTRjX7KGXQbXLJtm7xel8J2VhdCecXzxzY2
Mtnu3EXGNpFy9atTXxE/fI0C5AX/u2FDZiOHz9xV7sB3atcqAeXwJB0smpBnPbwI3BN+ptzsWnhyFNNS+ol4QayMDgFhi/tp2+lCAQ== |          7 | t          | 2006-02-14  | 2021-08-08 19:10:29.337653 |      1
           4 |        2 | jo3zKr6lJ5zMN5f3/BPgENhs9CdzDu7F/uFxAf6uO9MAKc7X+++ipk/OBbvvA8vpaJ7DTgph9LshRvpYIqPMwS6IubkScDOSRDoLBNW9z2oMF3dB46R86LK0pTEVVrGaddjnPzaAEh7Uwzy3LncC1y7pJqGKW1b3RGUE8n
4SgstMo/4tRNUe/AUcPn9FXkCoc5jFvn8gPwVoTRKwhYu0oTco2gNKZs1rmFdmkmobGaMjZuhWyWG2PO1oXIVPkpgILmD42yAMDxWkS4DVvgJChXJRukjBzfOitsQwHapjqPqf/q3mfBaQzNUwROcSfGBe6KlI5yfjWU309KRKaCYWNQ== | MMhPovG/N3k
Xjou6kS9V7QtEvlA5NS8/n62KVRVVGEnsh5bhwEhBZxlK72AQv8e4niATWPEiJJU6i7Z08NkU5FWNIvuWwlGTdEEW+kK7XQXib6cNAdnmo4RH72SWhDxEp3tMwwoZif2932H8WDEbNdP6bCP69ekBA7Z+nGtXaeh+H9BAaLf1e6XunBj2YN7zs4sFWB2Kxs2
IugLWd9g9677BWzUeJIzpJfVLro4HYlzASh9AMKb8wPRU0LlEpxtqUdejj7IY5M1hVhDTCCLSQjSqJscqzG1pYQ04W7KNdGwWxJPMjvyPC2K4H+HQuW0IWVjvFpyYd/5q1eIQX+vjdw== | oF4nyIjtVtWuydg6QiSg3BDadWe48nhbBEZSLrR5GVigA768
E3n1npN6sdstzG7bRVnphtfwIZwQ3MUFURWCbUCe0VqioNgVXFhiRvr3DAw2AH64ss/h65B2U5whAygnq4kiy5JvPD0z0omtfs9913QeoO+ilvPVLEc0q3n0jD9ZQlkNVfHSytx1NY86gWnESquTVhkVQ55QDV8GY70YLX9V6nU7ldu+zpNLmf2+rfpxqbRC
i16jnHGDcTT7CKeq+AxbiJDeaaAmSPpxTZsrX4sXFW4rpNtSmOyuyHZziy8rkN8xSpyhvrmxjC7EYe4bn6L/+hay108Wn0BSFYe2ow== |          8 | t          | 2006-02-14  | 2021-08-08 19:10:29.337653 |      1

```

Awesome, we get our data encrypted! How about the decrypt part of the class? Let's check it out:
```sql
test=# CREATE OR REPLACE FUNCTION getCustomerCrypto(int) RETURNS SETOF customer AS 'com.percona.blog.pljava.CustomerCrypto.getCustomerCrypto' LANGUAGE java;
CREATE FUNCTION

test=# SELECT * FROM getCustomerCrypto(10);
 customer_id | store_id | first_name | last_name |               email               | address_id | activebool | create_date |     last_update     | active 
-------------+----------+------------+-----------+-----------------------------------+------------+------------+-------------+---------------------+--------
          10 |        1 | DOROTHY    | TAYLOR    | DOROTHY.TAYLOR@sakilacustomer.org |         14 | t          | 2006-02-14  | 2006-02-15 09:57:20 |      1
(1 row)

test=# 
```

Worked like a charm! Here we finish this part 2 and at this point we are able to query and manipulate objects inside of our database. The next and last article of this serie will cover external calls and we will see how to use external resources from PL/Java, don't miss it!


<p>
<br />
[1] https://github.com/elchinoo/blogs/tree/main/pljava
</p>