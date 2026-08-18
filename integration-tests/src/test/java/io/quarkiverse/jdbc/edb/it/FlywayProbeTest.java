package io.quarkiverse.jdbc.edb.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs a Flyway migration over a {@code jdbc:edb:} URL and reads the result back.
 * <p>
 * Flyway resolves its {@code DatabaseType} from the product name reported by
 * {@code DatabaseMetaData}, and it ships no type for {@code EnterpriseDB}, which is what EPAS
 * reports. The {@code changeServerName=true} driver property makes the connection report
 * {@code PostgreSQL} instead, which is what lets {@code flyway-database-postgresql} accept it.
 * Without that property Flyway fails with {@code Unsupported Database: EnterpriseDB}.
 */
@QuarkusTest
@TestProfile(FlywayProbeTest.FlywayEnabled.class)
@WithTestResource(value = EdbDatabaseTestResource.class, initArgs = @ResourceArg(name = "urlParams", value = "changeServerName=true"))
public class FlywayProbeTest {

    @Inject
    Flyway flyway;

    @Inject
    DataSource dataSource;

    @Test
    public void appliesMigrationsOverEdbUrl() throws Exception {
        flyway.migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT note FROM flyway_probe WHERE id = 1")) {
            assertTrue(rows.next(), "flyway_probe should contain the row seeded by V1.0.0");
            assertEquals("migrated", rows.getString(1));
        }
    }

    public static class FlywayEnabled implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            // Hand the schema to Flyway: Hibernate's drop-and-create would otherwise populate it
            // first, and Flyway refuses to migrate a non-empty schema without a history table.
            // baseline-on-migrate additionally covers a persistent EPAS instance carrying tables
            // from an earlier run.
            return Map.of(
                    "quarkus.flyway.enabled", "true",
                    "quarkus.flyway.baseline-on-migrate", "true",
                    // Flyway's default baseline version is 1, and it normalises 1.0.0 to 1, which
                    // would mark V1.0.0 as already applied on a pre-existing schema.
                    "quarkus.flyway.baseline-version", "0",
                    "quarkus.hibernate-orm.schema-management.strategy", "none");
        }
    }
}
