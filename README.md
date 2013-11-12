JDBC lint
=========
JDBC lint helps Java programmers write correct and efficient code when using
the JDBC API.  JDBC lint requires Java 6 and has no other run-time
dependencies.  Andrew Gaul at Maginatics <gaul@maginatics.com> originally wrote
JDBC lint.

Features
--------
JDBC lint warns about many different conditions, configured via the following
properties:

* com.maginatics.jdbclint.blob.double\_free
* com.maginatics.jdbclint.blob.missing\_free
* com.maginatics.jdbclint.connection.double\_close
* com.maginatics.jdbclint.connection.missing\_close
* com.maginatics.jdbclint.connection.missing\_commit\_or\_rollback
* com.maginatics.jdbclint.connection.missing\_prepare\_statement
* com.maginatics.jdbclint.preparedstatement.double\_close
* com.maginatics.jdbclint.preparedstatement.missing\_close
* com.maginatics.jdbclint.preparedstatement.missing\_execute
* com.maginatics.jdbclint.preparedstatement.missing\_execute\_batch
* com.maginatics.jdbclint.resultset.double\_close
* com.maginatics.jdbclint.resultset.missing\_close
* com.maginatics.jdbclint.resultset.unread\_column
* com.maginatics.jdbclint.statement.double\_close
* com.maginatics.jdbclint.statement.missing\_close
* com.maginatics.jdbclint.statement.missing\_execute
* com.maginatics.jdbclint.statement.missing\_execute\_batch

JDBC lint enables all warnings by default and users can disable individual ones
by setting the corresponding property to false.

To make use of JDBC lint in an Apache Maven based project, add it as a
dependency:

```xml
<dependency>
    <groupId>com.maginatics</groupId>
    <artifactId>jdbclint</artifactId>
    <version>0.3.0</version>
</dependency>
```

Examples
--------
Users can enable it by wrapping their Connection or DataSource objects and
configure it via Java properties.  For example:

```java
import com.maginatics.jdbclint.ConnectionProxy;
...
Connection connection = DriverManager.getConnection(...);
connection = ConnectionProxy.newInstance(connection, new Properties());
connection.close();
connection.close();  // reports error and optionally throws exception
```

JDBC lint reports any errors to stderr by default and users can redirect this
to a file by setting com.maginatics.jdbclint.log\_file .  Users can also throw
a RuntimeException, SQLException, or exit on errors by setting
com.maginatics.jdbclint.fail\_method to throw\_runtime\_exception,
throw\_sql\_exception, or exit, respectively.

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
JDBC lint can work in conjunction with all of these tools.

License
-------
Copyright (C) 2012-2013 Maginatics, Inc.

Licensed under the Apache License, Version 2.0
