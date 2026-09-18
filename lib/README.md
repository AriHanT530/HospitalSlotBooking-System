# lib/

This folder is intentionally empty.

The project compiles and runs on a plain JDK 17 or newer with **no external
libraries**. Everything it needs comes from the standard library:

| Need | JDK package used |
|---|---|
| Collections, generics | `java.util` |
| File and console I/O | `java.io`, `java.nio.file` |
| Dates and times | `java.time` |
| Threads and locks | `java.lang`, `java.util.concurrent` |
| Password hashing | `java.security.MessageDigest` |
| JDBC API | `java.sql` |
| External configuration | `java.util.Properties` |

Only if you enable the optional JDBC backend do you need to place a driver jar
here (for example `mysql-connector-j-8.x.jar` or `sqlite-jdbc-3.x.jar`) and add
it to the classpath:

```bash
java -cp out:lib/* com.hospital.Main
```
