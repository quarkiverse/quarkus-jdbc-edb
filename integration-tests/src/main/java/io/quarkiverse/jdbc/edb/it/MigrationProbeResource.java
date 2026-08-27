package io.quarkiverse.jdbc.edb.it;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;

/**
 * Exposes the results of the Flyway and Liquibase migrations over HTTP.
 * <p>
 * Both tools run at application start against their own datasource, so by the time any of these
 * endpoints is reachable the migration has either succeeded or the application has failed to boot.
 * That is deliberate: it means a broken migration surfaces as a startup failure rather than as a
 * missing table halfway through a test.
 * <p>
 * Routed over HTTP for the same reason as {@link NamedDataSourceResource} -- it lets the assertions
 * run unchanged against the native executable, where injection is unavailable. Migrations are worth
 * covering in native specifically: both tools locate their scripts as classpath resources, which is
 * exactly the kind of thing a native image drops when it is not told to keep it.
 */
@Path("/migration")
@Produces(MediaType.TEXT_PLAIN)
public class MigrationProbeResource {

    /**
     * Flyway is given a schema of its own; see {@code application.properties}. Its objects are
     * therefore not on the connection's default search path and have to be qualified.
     */
    private static final String FLYWAY_SCHEMA = "probe_flyway";

    @Inject
    @DataSource("flyway")
    AgroalDataSource flywayDataSource;

    @Inject
    @DataSource("liquibase")
    AgroalDataSource liquibaseDataSource;

    @GET
    @Path("/flyway/note")
    public String flywayNote() throws SQLException {
        return queryString(flywayDataSource,
                "SELECT note FROM " + FLYWAY_SCHEMA + ".flyway_probe WHERE id = 1");
    }

    /**
     * Whether Flyway recorded {@code V1.0.0} as successfully applied. Asserting on this rather than
     * only on the probe table distinguishes "Flyway ran" from "the table happens to exist", which
     * matters on a persistent EPAS instance where an earlier run left one behind.
     * <p>
     * Filtered by version rather than counting successful rows outright: Flyway also records its own
     * bookkeeping there, the schema creation among it, so a plain count is not a stable number.
     */
    @GET
    @Path("/flyway/applied")
    public String flywayApplied() throws SQLException {
        return queryString(flywayDataSource,
                "SELECT count(*) FROM " + FLYWAY_SCHEMA
                        + ".flyway_schema_history WHERE version = '1.0.0' AND success = true");
    }

    @GET
    @Path("/liquibase/note")
    public String liquibaseNote() throws SQLException {
        return queryString(liquibaseDataSource, "SELECT note FROM liquibase_probe WHERE id = 1");
    }

    /**
     * The same distinction as {@link #flywayApplied()}, read from Liquibase's own tracking table.
     */
    @GET
    @Path("/liquibase/applied")
    public String liquibaseApplied() throws SQLException {
        return queryString(liquibaseDataSource,
                "SELECT count(*) FROM databasechangelog WHERE id = 'create-liquibase-probe'");
    }

    private static String queryString(AgroalDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : "no rows";
        }
    }
}
