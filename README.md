JDBC lint
=========
JDBC lint helps Java programmers write correct and efficient code when using
the JDBC API.  JDBC lint requires Java 6 and has no other run-time
dependencies.  Andrew Gaul at Maginatics <gaul@maginatics.com> originally wrote
JDBC lint.

Features
--------
JDBC lint warns about many different conditions:

* BLOB_DOUBLE_FREE
* BLOB_MISSING_FREE
* CONNECTION_DOUBLE_CLOSE
* CONNECTION_MISSING_CLOSE
* CONNECTION_MISSING_COMMIT_OR_ROLLBACK
* CONNECTION_MISSING_PREPARE_STATEMENT
* CONNECTION_MISSING_READ_ONLY
* PREPARED_STATEMENT_DOUBLE_CLOSE
* PREPARED_STATEMENT_MISSING_CLOSE
* PREPARED_STATEMENT_MISSING_EXECUTE
* PREPARED_STATEMENT_MISSING_EXECUTE_BATCH
* RESULT_SET_DOUBLE_CLOSE
* RESULT_SET_MISSING_CLOSE
* RESULT_SET_UNREAD_COLUMN
* STATEMENT_DOUBLE_CLOSE
* STATEMENT_MISSING_CLOSE
* STATEMENT_MISSING_EXECUTE
* STATEMENT_MISSING_EXECUTE_BATCH

Examples
--------
Users can enable JDBC lint by wrapping Connection or DataSource objects:

```java
import com.maginatics.jdbclint.Configuration;
import com.maginatics.jdbclint.ConnectionProxy;
...
Configuration config = Configuration.defaults().build()
Connection connection = DriverManager.getConnection(...);
connection = ConnectionProxy.newInstance(connection, config);
connection.close();
connection.close();  // reports error and optionally throws exception
```

Users can select from a predefined set of warnings via
```Configuration.defaults```, ```allEnabled```, and ```allDisabled```,
and configure individual warnings via ```Configuration.Builder.addCheck```
and ```removeCheck```.

JDBC lint reports any errors to stderr by default and users can redirect this
to a file by calling ```Configuration.Builder.setLogFile```.
Users can also throw a RuntimeException, SQLException, or exit on errors by
calling ```Configuration.Builder.setFailMethod```.

Installation
------------
To make use of JDBC lint in an Apache Maven based project, add it as a
dependency:

```xml
<dependency>
    <groupId>com.maginatics</groupId>
    <artifactId>jdbclint</artifactId>
    <version>0.4.0</version>
</dependency>
```

Background
----------
JDBC lint implements its checks by wrapping concrete implementations like
Connection with
[dynamic proxy classes](http://docs.oracle.com/javase/6/docs/api/java/lang/reflect/Proxy.html).
This allows JDBC lint to add its checks before and
after the concrete method invocation while preserving all behaviors of the
original class.  Some checks like warning about missing calls to close require
use of finalization which depends on the behavior of Java garbage collection.

References
----------
JDBC has significant complexity and other tools can help write correct code.
Specifically [FindBugs](http://findbugs.sourceforge.net/)
can detect failures to close SQL resources and Java 7
[try-with-resources](http://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)
can prevent the same mistakes.
[JDBI](http://jdbi.org/) offers an
annotation-based approach to writing SQL which avoids some kinds of errors.
[jOOQ](http://www.jooq.org) offers a fluent API that hides most interaction
with JDBC from client code.
JDBC lint can work in conjunction with all of these tools.

License
-------
Copyright (C) 2012-2014 Maginatics, Inc.

Licensed under the Apache License, Version 2.0
