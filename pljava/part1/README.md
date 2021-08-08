# PostgreSQL PL/Java – A How-To, Part 1

We’ve recently received some questions regarding PL/Java and I found it hard to get clear instructions searching on the internet. It’s not that there is no good information out there, but most of it is either incomplete, outdated, or confusing and I decided to create this short “how-to” and show how to install it and how to get it running with few examples.

## Installation
I will show here how to install it from sources, first because my platform doesn’t have the compiled binaries, and second because if your platform has the binaries from the package manager you can just install it from there, for example using YUM or APT. Also, note that I’m using PL/Java without the designation “<b>TRUSTED</b>” and a Postgres database superuser for simplicity. I would recommend reading the documentation about users and privileges [here[1]](https://tada.github.io/pljava/use/policy.html).

The versions of the software I’m using here are:

- PostgreSQL 12.7
- PL/Java 1.6.2
- OpenJDK 11
- Apache Maven 3.6.3

I downloaded the sources from “https://github.com/tada/pljava/releases“, unpackaged and compiled with maven:
```bash
wget https://github.com/tada/pljava/archive/refs/tags/V1_6_2.tar.gz
tar -xf V1_6_2.tar.gz
cd pljava-1_6_2
mvn clean install
java -jar pljava-packaging/target/pljava-pg12.jar
```

I’ll assume here that you know maven enough and won’t go through the “<b>mvn</b>” command. The "<b>java -jar pljava-packaging/target/pljava-pg12.jar</b>" will copy/install the needed files and packages into Postgres folders. Note that maven used my Postgres version and created the jar file with the version: “<b>pljava-pg12.jar</b>“, so pay attention to the version you have there as the jar file will change if you have a different Postgres version!

I can now create the extension into the database I will use it. I’m using the database “demo” in this blog:

```sql
$ psql demo
psql (12.7)
Type "help" for help.

demo=# CREATE EXTENSION pljava;
WARNING: Java virtual machine not yet loaded
DETAIL: libjvm: cannot open shared object file: No such file or directory
HINT: SET pljava.libjvm_location TO the correct path to the jvm library (libjvm.so or jvm.dll, etc.)
ERROR: cannot use PL/Java before successfully completing its setup
HINT: Check the log for messages closely preceding this one, detailing what step of setup failed and what will be needed, probably setting one of the "pljava." configuration variables, to complete the setup. If there is not enough help in the log, try again with different settings for "log_min_messages" or "log_error_verbosity".

demo=#
```

Not exactly what I was expecting but I got a good hint: “**HINT: SET pljava.libjvm_location TO the correct path to the jvm library (libjvm.so or jvm.dll, etc.)**“. Ok, I had to find the libjvm my system is using to configure Postgres. I used the SET command to do it online:

```sql
demo=# SET pljava.libjvm_location TO '/usr/lib/jvm/java-11-openjdk-11.0.11.0.9-5.fc34.x86_64/lib/server/libjvm.so';
NOTICE: PL/Java loaded
DETAIL: versions:
PL/Java native code (1.6.2)
PL/Java common code (1.6.2)
Built for (PostgreSQL 12.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 11.1.1 20210531 (Red Hat 11.1.1-3), 64-bit)
Loaded in (PostgreSQL 12.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 11.1.1 20210531 (Red Hat 11.1.1-3), 64-bit)
OpenJDK Runtime Environment (11.0.11+9)
OpenJDK 64-Bit Server VM (11.0.11+9, mixed mode, sharing)
NOTICE: PL/Java successfully started after adjusting settings
HINT: The settings that worked should be saved (using ALTER DATABASE demo SET ... FROM CURRENT or in the "/v01/data/db/pg12/postgresql.conf" file). For a reminder of what has been set, try: SELECT name, setting FROM pg_settings WHERE name LIKE 'pljava.%' AND source = 'session'
NOTICE: PL/Java load successful after failed CREATE EXTENSION
DETAIL: PL/Java is now installed, but not as an extension.
HINT: To correct that, either COMMIT or ROLLBACK, make sure the working settings are saved, exit this session, and in a new session, either: 1. if committed, run "CREATE EXTENSION pljava FROM unpackaged", or 2. if rolled back, simply "CREATE EXTENSION pljava" again.
SET

demo=# 
```

Also used the “**ALTER SYSTEM**” to make it persistent across all my databases as it writes the given parameter setting to the “postgresql.auto.conf” file, which is read in addition to “postgresql.conf“:

```sql
demo=# ALTER SYSTEM SET pljava.libjvm_location TO '/usr/lib/jvm/java-11-openjdk-11.0.11.0.9-5.fc34.x86_64/lib/server/libjvm.so';
ALTER SYSTEM

demo=# 
```

Now we have it installed we can check the system catalog if it is indeed there:
```sql
demo=# SELECT * FROM pg_language WHERE lanname LIKE 'java%';
oid    | lanname | lanowner | lanispl | lanpltrusted | lanplcallfoid | laninline | lanvalidator | lanacl
-------+---------+----------+---------+--------------+---------------+-----------+--------------+-------------------
16428  | java    | 10       | t       | t            | 16424         | 0         | 16427        | {charly=U/charly}
16429  | javau   | 10       | t       | f            | 16425         | 0         | 16426        |
(2 rows)

demo=# 
```

And test if it is working:
```sql
demo=# CREATE FUNCTION getProperty(VARCHAR)
RETURNS VARCHAR
AS 'java.lang.System.getProperty'
LANGUAGE java;
CREATE FUNCTION
demo=# SELECT getProperty('java.version');
getproperty
-------------
11.0.11
(1 row)

demo=# 
```

It’s working! Time to try something useful.

## Accessing Database Objects with PL/Java
The majority of the examples I found showed how to do a “hello world” from a Java class or how to calculate a Fibonacci sequence but nothing how to access database objects. Well, nothing wrong with those examples but I suppose that one who installs PL/Java in his database would like to access database objects from inside of a Java function and this is what we gonna do here.

I will use the sample database “**pagila**” that can be found [here[2]](https://www.postgresql.org/ftp/projects/pgFoundry/dbsamples/pagila/) for our tests in this post.

For this first example, I will create a simple class with a static method that will be accessed outside like any Postgres function. The function will receive an integer argument and use it to search the table “customer”, column “customer_id” and will print the customer’s id, full name, email,  and address:
```java
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
}
```

I’ve compiled and created the “jar” file manually with the below commands:
```bash
javac com/percona/blog/pljava/Customers.java
jar -c -f /app/pg12/lib/demo.jar com/percona/blog/pljava/Customers.class
```

Note that I’ve created the jar file inside the folder “**/app/pg12/lib**”, keep notes because we’ll use this information in the next step, loading the jar file inside Postgres:
```sql
demo=# SELECT sqlj.install_jar( 'file:///app/pg12/lib/demo.jar', 'demo', true );
 install_jar 
-------------
(1 row)

demo=# SELECT sqlj.set_classpath( 'public', 'demo' );
 set_classpath 
---------------
(1 row)

demo=# 
```

The **install_jar** function has the signature “**install_jar(<jar_url>, <jar_name>, <deploy>)**” and it loads a jar file from a location appointed by an URL into the SQLJ jar repository. It is an error if a jar with the given name already exists in the repository or if the jar doesn’t exist in the URL or the database isn’t able to read it:
```sql
demo=# SELECT sqlj.install_jar( 'file:///app/pg12/lib/<strong>demo2.jar</strong>', 'demo', true );
<strong>ERROR:  java.sql.SQLException: I/O exception reading jar file: /app/pg12/lib/demo2.jar (No such file or directory)
</strong>demo=# SELECT sqlj.install_jar( 'file:///app/pg12/lib/demo.jar', 'demo', true );
 install_jar 
------------- 
(1 row)

demo=# SELECT sqlj.install_jar( 'file:///app/pg12/lib/demo.jar', 'demo', true );
<strong>ERROR:  java.sql.SQLNonTransientException: A jar named 'demo' already exists
</strong>

demo=# 
```
The function **set_classpath** defines a classpath for the given schema, in this example the schema “**public**”. A classpath consists of a colon-separated list of jar names or class names. It’s an error if the given schema does not exist or if one or more jar names references non-existent jars.

The next step is to create the Postgres functions:
```sql
demo=# CREATE FUNCTION getCustomerInfo( INT ) RETURNS CHAR AS 
    'com.percona.blog.pljava.Customers.getCustomerInfo( java.lang.Integer )'
LANGUAGE java;
CREATE FUNCTION

demo=# 
```

We can now use it:
```sql
demo=# SELECT getCustomerInfo(100);
                                 getcustomerinfo                                  
----------------------------------------------------------------------------------
 - ID: 100                                                                       +
 - Name: HAYES, ROBIN                                                            +
 - Email: ROBIN.HAYES@sakilacustomer.org                                         +
 - Address: 1913 Kamakura Place                                                  +
 - City: Jelets                                                                  +
 - District: Lipetsk                                                             +
 --------------------------------------------------------------------------------
(1 row)

demo=# 
```

Sweet, we have our first Java function inside our Postgres demo database.

Now, in our last example here I will add another method to this class, now to list all the payments from a given customer and calculate its total:
```java
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
```

Same instructions to compile:
```bash
javac com/percona/blog/pljava/Customers.java 
jar -c -f /app/pg12/lib/demo.jar com/percona/blog/pljava/Customers.class
```

Then we need to replace the loaded jar file for the newly created and create the function inside Postgres:
```sql
demo=# SELECT sqlj.replace_jar( 'file:///app/pg12/lib/demo.jar', 'demo', true );
 replace_jar 
-------------
(1 row)

demo=# CREATE FUNCTION getCustomerTotal( INT ) RETURNS CHAR AS 
    'com.percona.blog.pljava.Customers.getCustomerTotal( java.lang.Integer )'
LANGUAGE java;
CREATE FUNCTION

demo=# 
```

And the result is:
```sql
test=# SELECT getCustomerTotal(9);
                                             getcustomertotal                                             
----------------------------------------------------------------------------------------------------------
 Customer ID  : 9                                                                                        +
 Customer Name: MOORE, MARGARET                                                                          +
 --------------------------------------------------------------------------------------------------------+
 Payment date: 2007-01-27 03:29:54.996577,    Value: 4.99                                                +
 Payment date: 2007-01-30 04:17:25.996577,    Value: 0.99                                                +
 Payment date: 2007-01-31 08:42:00.996577,    Value: 4.99                                                +
 Payment date: 2007-02-20 18:27:54.996577,    Value: 7.99                                                +
 Payment date: 2007-02-21 02:37:09.996577,    Value: 4.99                                                +
 Payment date: 2007-03-01 07:39:51.996577,    Value: 0.99                                                +
 Payment date: 2007-03-01 07:42:26.996577,    Value: 4.99                                                +
 Payment date: 2007-03-02 17:29:18.996577,    Value: 5.99                                                +
 Payment date: 2007-03-16 23:40:19.996577,    Value: 0.99                                                +
 Payment date: 2007-03-18 01:36:36.996577,    Value: 2.99                                                +
 Payment date: 2007-03-18 04:27:06.996577,    Value: 2.99                                                +
 Payment date: 2007-03-18 17:17:24.996577,    Value: 4.99                                                +
 Payment date: 2007-03-21 12:22:25.996577,    Value: 7.99                                                +
 Payment date: 2007-04-07 22:05:26.996577,    Value: 2.99                                                +
 Payment date: 2007-04-08 12:28:04.996577,    Value: 0.99                                                +
 Payment date: 2007-04-08 15:04:10.996577,    Value: 1.99                                                +
 Payment date: 2007-04-10 06:14:06.996577,    Value: 2.99                                                +
 Payment date: 2007-04-11 00:36:55.996577,    Value: 4.99                                                +
 Payment date: 2007-04-11 08:45:55.996577,    Value: 5.99                                                +
 Payment date: 2007-04-27 22:43:52.996577,    Value: 0.99                                                +
 Payment date: 2007-04-28 06:20:22.996577,    Value: 2.99                                                +
 Payment date: 2007-04-30 05:02:33.996577,    Value: 4.99                                                +
 Payment date: 2007-05-14 13:44:29.996577,    Value: 4.99                                                +
 --------------------------------------------------------------------------------------------------------+
 Total: 89.77
(1 row)

test=#
```

We finish this part here and with this last example. At this point, we are able to access objects, loop through a resultset, and return the result back as a single object like a TEXT. I will discuss how to return an array/resultset, how to use PL/Java functions within triggers, and how to use external resources in part two and part three of this article, stay tuned!

<p>
<br />
[1] https://tada.github.io/pljava/use/policy.html <br />
[2] https://www.postgresql.org/ftp/projects/pgFoundry/dbsamples/pagila/
</p>