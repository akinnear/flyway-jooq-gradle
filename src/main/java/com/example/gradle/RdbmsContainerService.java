package com.example.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class RdbmsContainerService implements BuildService<RdbmsContainerService.Params>, AutoCloseable {

    public interface Params extends BuildServiceParameters {
        Property<SupportedDatabase> getDatabaseType();
        Property<String> getDockerImage();
        Property<String> getUsername();
        Property<String> getPassword();
        ListProperty<String> getDatabaseNames();
    }

    private JdbcDatabaseContainer<?> container;
    private final Set<String> initializedNames = new LinkedHashSet<>();

    public synchronized void ensureDatabases(List<String> namesFromJooq) {
        SupportedDatabase dbType = getParameters().getDatabaseType().get();
        List<String> desiredNames = normalize(namesFromJooq);
        if (desiredNames.isEmpty()) {
            desiredNames = normalize(getParameters().getDatabaseNames().getOrElse(List.of("app")));
        }
        if (desiredNames.isEmpty()) {
            desiredNames = List.of("app");
        }

        if (container == null) {
            startContainer(dbType, desiredNames.get(0));
            initializedNames.add(desiredNames.get(0));
        }

        List<String> extra = desiredNames.stream().filter(name -> !initializedNames.contains(name)).toList();
        if (!extra.isEmpty()) {
            createAdditional(dbType, extra);
            initializedNames.addAll(extra);
        }
    }

    public String getJdbcUrl() {
        ensureDatabases(List.of());
        return container.getJdbcUrl();
    }

    public String getUsername() {
        ensureDatabases(List.of());
        return container.getUsername();
    }

    public String getPassword() {
        ensureDatabases(List.of());
        return container.getPassword();
    }

    public String getJdbcDriver() {
        return getParameters().getDatabaseType().get().getJdbcDriver();
    }

    public String getJooqDatabaseClass() {
        return getParameters().getDatabaseType().get().getJooqDatabaseClass();
    }

    private void startContainer(SupportedDatabase dbType, String primaryName) {
        String image = getParameters().getDockerImage().isPresent()
            ? getParameters().getDockerImage().get()
            : dbType.getDefaultImage();
        String user = getParameters().getUsername().get();
        String pass = getParameters().getPassword().get();

        switch (dbType) {
            case POSTGRES -> container = new PostgreSQLContainer<>(image)
                .withDatabaseName(primaryName)
                .withUsername(user)
                .withPassword(pass);
            case MYSQL -> container = new MySQLContainer<>(image)
                .withDatabaseName(primaryName)
                .withUsername(user)
                .withPassword(pass);
            default -> container = new MariaDBContainer<>(image)
                .withDatabaseName(primaryName)
                .withUsername(user)
                .withPassword(pass);
        }
        container.start();
    }

    private void createAdditional(SupportedDatabase dbType, List<String> names) {
        try (Connection conn = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement stmt = conn.createStatement()) {
            for (String name : names) {
                String escaped = name.replace("`", "``").replace("\"", "\"\"");
                if (dbType == SupportedDatabase.POSTGRES) {
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + escaped + "\"");
                } else {
                    stmt.execute("CREATE DATABASE IF NOT EXISTS `" + escaped + "`");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create additional databases/schemas", e);
        }
    }

    private static List<String> normalize(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        normalized.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    @Override
    public synchronized void close() {
        if (container != null) {
            container.stop();
            container = null;
            initializedNames.clear();
        }
    }
}
