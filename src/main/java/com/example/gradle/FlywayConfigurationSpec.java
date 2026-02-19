package com.example.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;

import javax.inject.Inject;

public abstract class FlywayConfigurationSpec {
    private final ListProperty<String> migrationLocations;
    private final MapProperty<String, String> options;

    @Inject
    public FlywayConfigurationSpec(ObjectFactory objects) {
        this.migrationLocations = objects.listProperty(String.class);
        this.options = objects.mapProperty(String.class, String.class);
    }

    public ListProperty<String> getMigrationLocations() {
        return migrationLocations;
    }

    public MapProperty<String, String> getOptions() {
        return options;
    }
}
