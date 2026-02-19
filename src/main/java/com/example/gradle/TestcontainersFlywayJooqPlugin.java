package com.example.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.TaskCollection;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TestcontainersFlywayJooqPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.flywaydb.flyway");
        project.getPluginManager().apply("nu.studer.jooq");

        TestcontainersJooqExtension extension =
            project.getExtensions().create("testcontainersJooq", TestcontainersJooqExtension.class);

        BuildServiceRegistry services = project.getGradle().getSharedServices();
        BuildServiceRegistration<RdbmsContainerService, RdbmsContainerService.Params> registration =
            services.registerIfAbsent("rdbmsCodegenService", RdbmsContainerService.class, spec -> {
                spec.getParameters().getDatabaseType().set(extension.getDatabaseType());
                spec.getParameters().getDockerImage().set(extension.getDockerImage());
                spec.getParameters().getUsername().set(extension.getUsername());
                spec.getParameters().getPassword().set(extension.getPassword());
                spec.getParameters().getDatabaseNames().set(extension.getDatabaseNames());
            });

        project.afterEvaluate(p -> {
            Object jooqExt = project.getExtensions().findByName("jooq");
            if (jooqExt == null) {
                return;
            }

            TaskCollection<Task> generateTasks = project.getTasks().matching(task -> {
                String name = task.getName();
                return name.toLowerCase(Locale.ROOT).startsWith("generate") && name.endsWith("Jooq");
            });

            generateTasks.configureEach(task -> {
                task.dependsOn(project.getTasks().named("flywayMigrate"));
                task.usesService(registration.getService());
                task.doFirst(t -> {
                    RdbmsContainerService service = registration.getService().get();
                    service.ensureDatabases(resolveSchemaNames(extension, jooqExt));

                    Object jooqCfg = findJooqConfigurationForTask(jooqExt, task.getName());
                    if (jooqCfg != null) {
                        SchemaConfigurationSpec configSpec = findSchemaSpecForTask(extension, task.getName());
                        configureJooq(jooqCfg, service, extension, configSpec);
                    }
                });
            });

            Task flywayTask = project.getTasks().named("flywayMigrate").get();
            flywayTask.usesService(registration.getService());
            flywayTask.doFirst(t -> {
                RdbmsContainerService service = registration.getService().get();
                service.ensureDatabases(resolveSchemaNames(extension, jooqExt));

                Object flywayExt = project.getExtensions().getByName("flyway");
                invokeSet(flywayExt, "setUrl", service.getJdbcUrl());
                invokeSet(flywayExt, "setUser", service.getUsername());
                invokeSet(flywayExt, "setPassword", service.getPassword());

                List<SchemaConfigurationSpec> selected = selectSchemaConfigsForInvocation(project, extension);
                LinkedHashSet<String> locations = new LinkedHashSet<>();
                LinkedHashSet<String> schemas = new LinkedHashSet<>();
                for (SchemaConfigurationSpec spec : selected) {
                    FlywayConfigurationSpec flywaySpec = spec.getFlywayConfiguration();
                    locations.addAll(normalize(flywaySpec.getMigrationLocations().getOrElse(List.of())));
                    String schema = effectiveInputSchema(spec);
                    if (!isBlank(schema)) {
                        schemas.add(schema.trim());
                    }
                    applySetterMap(flywayExt, flywaySpec.getOptions().getOrElse(Map.of()));
                }
                if (!locations.isEmpty()) {
                    invokeSet(flywayExt, "setLocations", new ArrayList<>(locations));
                }
                if (!schemas.isEmpty()) {
                    invokeSet(flywayExt, "setSchemas", new ArrayList<>(schemas));
                }
            });

            SupportedDatabase dbType = extension.getDatabaseType().get();
            String driverDep = extension.getJdbcDriverDependency().getOrElse(dbType.getDefaultJdbcDriverDependency());
            DependencyHandler dependencies = project.getDependencies();
            dependencies.add("jooqGenerator", driverDep);
        });
    }

    private static void configureJooq(Object jooqCfg, RdbmsContainerService service,
                                      TestcontainersJooqExtension extension, SchemaConfigurationSpec spec) {
        Object jdbc = invoke(jooqCfg, "getJdbc");
        invokeSet(jdbc, "setUrl", service.getJdbcUrl());
        invokeSet(jdbc, "setUser", service.getUsername());
        invokeSet(jdbc, "setPassword", service.getPassword());
        invokeSet(jdbc, "setDriver", service.getJdbcDriver());

        Object generator = invoke(jooqCfg, "getGenerator");
        Object database = invoke(generator, "getDatabase");

        if (isBlank(getString(invoke(database, "getName")))) {
            invokeSet(database, "setName", service.getJooqDatabaseClass());
        }

        JooqGeneratorSpec jooqSpec = spec == null ? null : spec.getJooqGenerator();
        String configuredInputSchema = getString(invoke(database, "getInputSchema"));
        Object inputSchemata = invoke(database, "getInputSchemata");
        boolean hasInputSchemata = inputSchemata instanceof List<?> list && !list.isEmpty();
        if (isBlank(configuredInputSchema) && !hasInputSchemata) {
            String preferred = effectiveInputSchema(spec);
            if (isBlank(preferred)) {
                List<String> defaults = normalize(extension.getDatabaseNames().getOrElse(List.of()));
                preferred = defaults.isEmpty() ? null : defaults.get(0);
            }
            if (!isBlank(preferred)) {
                invokeSet(database, "setInputSchema", preferred);
            }
        }

        if (jooqSpec != null) {
            String overrideInputSchema = jooqSpec.getInputSchema().getOrNull();
            if (!isBlank(overrideInputSchema)) {
                invokeSet(database, "setInputSchema", overrideInputSchema);
            }
            List<String> overrideSchemata = normalize(jooqSpec.getInputSchemata().getOrElse(List.of()));
            if (!overrideSchemata.isEmpty()) {
                invokeSet(database, "setInputSchemata", overrideSchemata);
            }
            if (!isBlank(jooqSpec.getIncludes().getOrNull())) {
                invokeSet(database, "setIncludes", jooqSpec.getIncludes().get());
            }
            if (!isBlank(jooqSpec.getExcludes().getOrNull())) {
                invokeSet(database, "setExcludes", jooqSpec.getExcludes().get());
            }

            applySetterMap(database, jooqSpec.getDatabaseOptions().getOrElse(Map.of()));
            applySetterMap(generator, jooqSpec.getGeneratorOptions().getOrElse(Map.of()));

            Object target = invoke(generator, "getTarget");
            if (!isBlank(jooqSpec.getTargetPackage().getOrNull())) {
                invokeSet(target, "setPackageName", jooqSpec.getTargetPackage().get());
            }
            if (!isBlank(jooqSpec.getTargetDirectory().getOrNull())) {
                invokeSet(target, "setDirectory", jooqSpec.getTargetDirectory().get());
            }
            applySetterMap(target, jooqSpec.getTargetOptions().getOrElse(Map.of()));
        }
    }

    private static List<SchemaConfigurationSpec> selectSchemaConfigsForInvocation(Project project, TestcontainersJooqExtension extension) {
        List<String> requested = project.getGradle().getStartParameter().getTaskNames();
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(extension.getConfigurations());
        }
        LinkedHashSet<SchemaConfigurationSpec> selected = new LinkedHashSet<>();
        for (String req : requested) {
            String plain = req.contains(":") ? req.substring(req.lastIndexOf(':') + 1) : req;
            SchemaConfigurationSpec byTask = findSchemaSpecForTask(extension, plain);
            if (byTask != null) {
                selected.add(byTask);
            }
        }
        return selected.isEmpty() ? new ArrayList<>(extension.getConfigurations()) : new ArrayList<>(selected);
    }

    private static SchemaConfigurationSpec findSchemaSpecForTask(TestcontainersJooqExtension extension, String taskName) {
        for (SchemaConfigurationSpec spec : extension.getConfigurations()) {
            if (taskNameForConfig(spec.getName()).equals(taskName)) {
                return spec;
            }
        }
        return null;
    }

    private static List<String> resolveSchemaNames(TestcontainersJooqExtension extension, Object jooqExt) {
        LinkedHashSet<String> names = new LinkedHashSet<>(normalize(extension.getDatabaseNames().getOrElse(List.of())));

        for (SchemaConfigurationSpec spec : extension.getConfigurations()) {
            String schema = effectiveInputSchema(spec);
            if (!isBlank(schema)) {
                names.add(schema.trim());
            }
            names.addAll(normalize(spec.getJooqGenerator().getInputSchemata().getOrElse(List.of())));
        }

        Object configurations = invoke(jooqExt, "getConfigurations");
        if (configurations instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                Object jooqCfg = invoke(entryValue, "getJooqConfiguration");
                Object generator = invoke(jooqCfg, "getGenerator");
                Object database = invoke(generator, "getDatabase");

                String inputSchema = getString(invoke(database, "getInputSchema"));
                if (!isBlank(inputSchema)) {
                    names.add(inputSchema.trim());
                }
                Object inputSchemata = invoke(database, "getInputSchemata");
                if (inputSchemata instanceof List<?> list) {
                    for (Object item : list) {
                        String schema = getString(item);
                        if (!isBlank(schema)) {
                            names.add(schema.trim());
                        }
                    }
                }
            }
        }

        List<String> normalized = new ArrayList<>(names);
        return normalized.isEmpty() ? List.of("app") : normalized;
    }

    private static String effectiveInputSchema(SchemaConfigurationSpec spec) {
        if (spec == null) {
            return null;
        }
        String fromTopLevel = spec.getInputSchema().getOrNull();
        if (!isBlank(fromTopLevel)) {
            return fromTopLevel;
        }
        String fromJooq = spec.getJooqGenerator().getInputSchema().getOrNull();
        if (!isBlank(fromJooq)) {
            return fromJooq;
        }
        return null;
    }

    private static Object findJooqConfigurationForTask(Object jooqExt, String taskName) {
        Object configurations = invoke(jooqExt, "getConfigurations");
        if (!(configurations instanceof Map<?, ?> map)) {
            return null;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String configName)) {
                continue;
            }
            if (taskName.equals(taskNameForConfig(configName))) {
                return invoke(entry.getValue(), "getJooqConfiguration");
            }
        }
        return null;
    }

    private static String taskNameForConfig(String configName) {
        if ("main".equals(configName)) {
            return "generateJooq";
        }
        return "generate" + Character.toUpperCase(configName.charAt(0)) + configName.substring(1) + "Jooq";
    }

    private static void applySetterMap(Object target, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String raw = entry.getKey();
            if (isBlank(raw)) {
                continue;
            }
            String setter = "set" + Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            invokeSet(target, setter, entry.getValue());
        }
    }

    private static List<String> normalize(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void invokeSet(Object target, String methodName, Object arg) {
        if (target == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                Object converted = convertArg(method.getParameterTypes()[0], arg);
                if (converted != null || arg == null) {
                    method.invoke(target, converted);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
                // try next overload
            }
        }
    }

    private static Object convertArg(Class<?> targetType, Object arg) {
        if (arg == null) {
            return null;
        }
        if (targetType.isInstance(arg)) {
            return arg;
        }
        if (List.class.isAssignableFrom(targetType) && arg instanceof List<?>) {
            return arg;
        }
        String value = arg.toString();
        if (targetType == String.class) {
            return value;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    private static String getString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
