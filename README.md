# Testcontainers + Flyway + jOOQ Gradle plugin

Plugin ID: `com.example.testcontainers-flyway-jooq`

Implementation language: Java.

## Configuration style

You can now define schema-scoped behavior under your custom plugin block, including:
- `flywayConfiguration { migrationLocations = [...] }`
- `jooqGenerator { ... }`

```groovy
plugins {
  id 'com.example.testcontainers-flyway-jooq' version '0.1.0'
}

import com.example.gradle.SupportedDatabase

testcontainersJooq {
  databaseType = SupportedDatabase.POSTGRES
  // dockerImage = 'postgres:17'
  username = 'app'
  password = 'app'

  // optional override
  // jdbcDriverDependency = 'org.postgresql:postgresql:42.7.7'

  configurations {
    schema1 {
      inputSchema = 'schema1'

      flywayConfiguration {
        migrationLocations = ['filesystem:src/main/resources/db/migration/schema1']
        // additional Flyway extension options via setter name (without "set")
        options.put('baselineOnMigrate', 'true')
      }

      jooqGenerator {
        // defaults are applied when omitted
        // inputSchema = 'schema1'
        includes = '.*'
        excludes = ''
        targetPackage = 'com.example.jooq.schema1'
        targetDirectory = "$buildDir/generated-src/jooq/schema1"

        // extensibility maps
        databaseOptions.put('includeTables', 'true')
        generatorOptions.put('generatePojos', 'true')
        targetOptions.put('clean', 'true')
      }
    }
  }
}

// Native jOOQ configs still exist and are matched by name to generate tasks.
jooq {
  configurations {
    schema1 {
      jooqConfiguration {
        generator { database { } }
      }
    }
  }
}
```

## Behavior

- Same Testcontainers DB credentials are injected into Flyway and jOOQ runtime.
- `configurations.<name>` maps to `generate<Name>Jooq` (`main` maps to `generateJooq`).
- jOOQ defaults (applied when missing):
  - `generator.database.name` from `databaseType`
  - `generator.database.inputSchema` from config `inputSchema` (or first `databaseNames` fallback)
- Flyway locations/schemas are derived from selected custom configurations when running generation tasks.
- Databases/schemas created in Testcontainers are resolved from:
  - `testcontainersJooq.databaseNames`
  - each custom config `inputSchema` / `jooqGenerator.inputSchemata`
  - existing native jOOQ `inputSchema` / `inputSchemata` values.

For MySQL/MariaDB, additional names are created as databases.
For PostgreSQL, additional names are created as schemas.
