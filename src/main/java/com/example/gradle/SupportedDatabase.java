package com.example.gradle;

public enum SupportedDatabase {
    MARIADB("org.mariadb.jdbc.Driver", "org.jooq.meta.mariadb.MariaDBDatabase", "mariadb:11.4", "org.mariadb.jdbc:mariadb-java-client:3.5.1"),
    MYSQL("com.mysql.cj.jdbc.Driver", "org.jooq.meta.mysql.MySQLDatabase", "mysql:8.4", "com.mysql:mysql-connector-j:9.3.0"),
    POSTGRES("org.postgresql.Driver", "org.jooq.meta.postgres.PostgresDatabase", "postgres:17", "org.postgresql:postgresql:42.7.5");

    private final String jdbcDriver;
    private final String jooqDatabaseClass;
    private final String defaultImage;
    private final String defaultJdbcDriverDependency;

    SupportedDatabase(String jdbcDriver, String jooqDatabaseClass, String defaultImage, String defaultJdbcDriverDependency) {
        this.jdbcDriver = jdbcDriver;
        this.jooqDatabaseClass = jooqDatabaseClass;
        this.defaultImage = defaultImage;
        this.defaultJdbcDriverDependency = defaultJdbcDriverDependency;
    }

    public String getJdbcDriver() {
        return jdbcDriver;
    }

    public String getJooqDatabaseClass() {
        return jooqDatabaseClass;
    }

    public String getDefaultImage() {
        return defaultImage;
    }

    public String getDefaultJdbcDriverDependency() {
        return defaultJdbcDriverDependency;
    }
}
