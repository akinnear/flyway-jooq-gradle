package com.example.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class JooqGeneratorSpec {
    private final Property<String> inputSchema;
    private final ListProperty<String> inputSchemata;
    private final Property<String> includes;
    private final Property<String> excludes;
    private final Property<String> targetPackage;
    private final Property<String> targetDirectory;
    private final MapProperty<String, String> databaseOptions;
    private final MapProperty<String, String> generatorOptions;
    private final MapProperty<String, String> targetOptions;

    @Inject
    public JooqGeneratorSpec(ObjectFactory objects) {
        this.inputSchema = objects.property(String.class);
        this.inputSchemata = objects.listProperty(String.class);
        this.includes = objects.property(String.class);
        this.excludes = objects.property(String.class);
        this.targetPackage = objects.property(String.class);
        this.targetDirectory = objects.property(String.class);
        this.databaseOptions = objects.mapProperty(String.class, String.class);
        this.generatorOptions = objects.mapProperty(String.class, String.class);
        this.targetOptions = objects.mapProperty(String.class, String.class);
    }

    public Property<String> getInputSchema() { return inputSchema; }
    public ListProperty<String> getInputSchemata() { return inputSchemata; }
    public Property<String> getIncludes() { return includes; }
    public Property<String> getExcludes() { return excludes; }
    public Property<String> getTargetPackage() { return targetPackage; }
    public Property<String> getTargetDirectory() { return targetDirectory; }
    public MapProperty<String, String> getDatabaseOptions() { return databaseOptions; }
    public MapProperty<String, String> getGeneratorOptions() { return generatorOptions; }
    public MapProperty<String, String> getTargetOptions() { return targetOptions; }
}
