package com.example.gradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class TestcontainersJooqExtension {
    private final Property<SupportedDatabase> databaseType;
    private final Property<String> dockerImage;
    private final Property<String> username;
    private final Property<String> password;
    private final Property<String> jdbcDriverDependency;
    private final ListProperty<String> databaseNames;
    private final NamedDomainObjectContainer<SchemaConfigurationSpec> configurations;

    @Inject
    public TestcontainersJooqExtension(ObjectFactory objects) {
        this.databaseType = objects.property(SupportedDatabase.class).convention(SupportedDatabase.MARIADB);
        this.dockerImage = objects.property(String.class);
        this.username = objects.property(String.class).convention("app");
        this.password = objects.property(String.class).convention("app");
        this.jdbcDriverDependency = objects.property(String.class);
        this.databaseNames = objects.listProperty(String.class);
        this.configurations = objects.domainObjectContainer(SchemaConfigurationSpec.class,
            name -> objects.newInstance(SchemaConfigurationSpec.class, name));
    }

    public Property<SupportedDatabase> getDatabaseType() { return databaseType; }
    public Property<String> getDockerImage() { return dockerImage; }
    public Property<String> getUsername() { return username; }
    public Property<String> getPassword() { return password; }
    public Property<String> getJdbcDriverDependency() { return jdbcDriverDependency; }
    public ListProperty<String> getDatabaseNames() { return databaseNames; }
    public NamedDomainObjectContainer<SchemaConfigurationSpec> getConfigurations() { return configurations; }
}
