package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that both schema migration tools run over a {@code jdbc:edb:} URL.
 * <p>
 * Each runs at start against a datasource of its own, so the application would not have booted had a
 * migration failed. The assertions therefore confirm two separate things: that the migration produced
 * what it should, and -- from each tool's own tracking table -- that it genuinely ran rather than
 * finding the work already done by an earlier run against a persistent database.
 * <p>
 * The interesting case is {@link MigrationProbeResourceIT}, against the native executable. Both tools
 * locate their scripts as classpath resources and instantiate database implementations reflectively,
 * neither of which survives a native image by default.
 *
 * @see MigrationProbeResourceIT for the same assertions against the native executable
 */
@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class MigrationProbeResourceTest {

    @Test
    public void flywayAppliedItsMigration() {
        given()
                .when().get("/migration/flyway/applied")
                .then().statusCode(200)
                .body(is("1"));
    }

    @Test
    public void flywayMigrationProducedItsRow() {
        given()
                .when().get("/migration/flyway/note")
                .then().statusCode(200)
                .body(is("migrated"));
    }

    @Test
    public void liquibaseAppliedItsChangeSet() {
        given()
                .when().get("/migration/liquibase/applied")
                .then().statusCode(200)
                .body(is("1"));
    }

    @Test
    public void liquibaseChangeSetProducedItsRow() {
        given()
                .when().get("/migration/liquibase/note")
                .then().statusCode(200)
                .body(is("migrated"));
    }
}
