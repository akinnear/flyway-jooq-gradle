package com.example.gradle;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class SchemaConfigurationSpec implements Named {
    private final String name;
    private final Property<String> inputSchema;
    private final FlywayConfigurationSpec flywayConfiguration;
    private final JooqGeneratorSpec jooqGenerator;

    @Inject
    public SchemaConfigurationSpec(String name, ObjectFactory objects) {
        this.name = name;
        this.inputSchema = objects.property(String.class);
        this.flywayConfiguration = objects.newInstance(FlywayConfigurationSpec.class);
        this.jooqGenerator = objects.newInstance(JooqGeneratorSpec.class);
    }

    @Override
    public String getName() {
        return name;
    }

    public Property<String> getInputSchema() {
        return inputSchema;
    }

    public FlywayConfigurationSpec getFlywayConfiguration() {
        return flywayConfiguration;
    }

    public void flywayConfiguration(Action<? super FlywayConfigurationSpec> action) {
        action.execute(flywayConfiguration);
    }

    public JooqGeneratorSpec getJooqGenerator() {
        return jooqGenerator;
    }

    public void jooqGenerator(Action<? super JooqGeneratorSpec> action) {
        action.execute(jooqGenerator);
    }
}
