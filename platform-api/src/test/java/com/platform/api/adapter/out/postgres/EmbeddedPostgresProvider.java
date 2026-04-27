package com.platform.api.adapter.out.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Starts a single embedded PostgreSQL instance per JVM and runs Liquibase migrations once.
 * Shared across all JDBC repository integration tests to keep total setup time low.
 */
public final class EmbeddedPostgresProvider {

    public static final DataSource DATA_SOURCE;

    static {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.start();
            DATA_SOURCE = pg.getPostgresDatabase();
            runMigrations(DATA_SOURCE);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private EmbeddedPostgresProvider() {}

    private static void runMigrations(DataSource ds) throws Exception {
        try (Connection conn = ds.getConnection()) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(),
                    database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }
}
