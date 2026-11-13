package com.example.demo.codegen;

import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class GenerateJooq {
    private static final String POSTGRES_IMAGE = "postgres:latest";
    private static final String GENERATED_PACKAGE = "com.example.demo.events.outbound.jooq";

    private GenerateJooq() {
    }

    public static void main(String[] args) throws Exception {
        Path migrationDirectory = Path.of(args[0]);
        Path generatedDirectory = Path.of(args[1]);
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE));

        try {
            postgres.start();
            migrate(postgres, migrationDirectory);
            generate(postgres, generatedDirectory);
        } finally {
            if (postgres.isRunning()) {
                postgres.stop();
            }
        }
    }

    private static void migrate(PostgreSQLContainer<?> postgres, Path migrationDirectory) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .load()
                .migrate();
    }

    private static void generate(PostgreSQLContainer<?> postgres, Path generatedDirectory) throws Exception {
        clearGeneratedDirectory(generatedDirectory);

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.getJdbcUrl())
                        .withUser(postgres.getUsername())
                        .withPassword(postgres.getPassword()))
                .withGenerator(new Generator()
                        .withName("org.jooq.codegen.JavaGenerator")
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema("public")
                                .withIncludes("events|registrations"))
                        .withTarget(new Target()
                                .withPackageName(GENERATED_PACKAGE)
                                .withDirectory(generatedDirectory.toAbsolutePath().toString())));

        GenerationTool.generate(configuration);
    }

    private static void clearGeneratedDirectory(Path generatedDirectory) throws IOException {
        if (Files.exists(generatedDirectory)) {
            try (var paths = Files.walk(generatedDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(generatedDirectory);
    }
}
