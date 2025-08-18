# Sopra

Sopra is a lightweight annotation-based ORM for Java, built on top of JDBC and powered by [HikariCP](https://github.com/brettwooldridge/HikariCP).  
It provides simple annotations for mapping Java classes to SQL tables, while automatically handling schema generation, connections, and constraints.


## Features
- Simple **annotation-based** entity mapping
- Automatic **table creation** from classes
- Supports **primary keys**, **unique constraints**, **default values**, and **on update** expressions
- Built-in **connection manager** with multiple data sources
- Automatic **snake_case** conversion for database columns

## Installation
_Coming soon..._

## Setup

```java
ObjectNode node = new ObjectMapper().createObjectNode();
node.put("serverName", "localhost");
node.put("databaseName", "sopra_test");
node.put("user", "root");
node.put("password", "");
node.put("port", 3306);
node.put("id", "default");

Map<String, Object> defaultProperties = Map.of(
        "useSSL", false,
        "verifyServerCertificate", false,
        "characterEncoding", "utf8"
);

SopraApi sopra = SopraApi.Builder.create(SopraApi.class)
        .setCredentials(node)
        .setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource")
        .setDefaultProperties(defaultProperties)
        .build();

try (Connection connection = sopra.getConnection()) {
    System.out.println("Retrieved connection to:" + connection.getMetaData().getURL());
} catch (SQLException e) {
    throw new RuntimeException("Failed to get connection", e);
}
```

## Define a table

```java
@SopraTable(dataSourceId = "default", name = "accounts", snakeCase = true)
@UniqueConstraint(columns = {"username", "email"})
public class Account {

    @Id
    @AutoIncrement
    private int id;

    @Column(length = 128)
    @Unique
    private String username;

    @Column(length = 128)
    @Unique
    private String email;

    private String passwordHash;

    @Default(value = "'[]'")
    private List<String> logs;

    @OnUpdate(value = "CURRENT_TIMESTAMP")
    private Timestamp updatedAt;

    @Default(value = "CURRENT_TIMESTAMP")
    private Timestamp createdAt;
}
```

This will generate and execute the following SQL table:
```sql
CREATE TABLE IF NOT EXISTS accounts (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(128) NOT NULL UNIQUE,
  email VARCHAR(128) NOT NULL UNIQUE,
  password_hash VARCHAR(255),
  logs LONGTEXT DEFAULT '[]',
  updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (username, email)
);
```

## License

This project is licensed under Apache License 2.0. See the [LICENSE](LICENSE.md) file for more details.