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
 * Exposes the state of a named datasource over HTTP.
 * <p>
 * The assertions this supports could be made by injecting the datasource straight into a test, but
 * only in JVM mode: {@code @QuarkusIntegrationTest} runs against the native executable out of
 * process, where injection is unavailable. Routing them through endpoints is what lets
 * {@link NamedDataSourceResourceTest} be re-run against the native image, which is the case that
 * matters most -- native is where CDI bean removal and reflective driver instantiation can differ.
 */
@Path("/named-datasource")
@Produces(MediaType.TEXT_PLAIN)
public class NamedDataSourceResource {

    @Inject
    AgroalDataSource defaultDataSource;

    @Inject
    @DataSource("secondary")
    AgroalDataSource secondaryDataSource;

    /**
     * The implementation class of the driver's own connection, which is the unambiguous proof that
     * the EDB driver served this datasource. The unwrap is required because Agroal hands out a
     * pooled wrapper rather than the driver's connection.
     */
    @GET
    @Path("/connection-class")
    public String connectionClass() throws SQLException {
        try (Connection connection = secondaryDataSource.getConnection()) {
            return connection.unwrap(Connection.class).getClass().getName();
        }
    }

    /**
     * Proves {@code EdbAgroalConnectionConfigurer} was selected for this datasource. Without the
     * {@code @JdbcDriver("edb")} bean being resolved per datasource, Agroal falls back to
     * {@code UnknownDbAgroalConnectionConfigurer}, which leaves the exception sorter unset.
     */
    @GET
    @Path("/exception-sorter")
    public String exceptionSorter() {
        return secondaryDataSource.getConfiguration()
                .connectionPoolConfiguration()
                .exceptionSorter()
                .getClass()
                .getSimpleName();
    }

    @GET
    @Path("/query")
    public String query() throws SQLException {
        try (Connection connection = secondaryDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 1")) {
            return rows.next() ? String.valueOf(rows.getInt(1)) : "no rows";
        }
    }

    @GET
    @Path("/distinct")
    public String distinctFromDefaultDataSource() {
        return String.valueOf(defaultDataSource != secondaryDataSource);
    }
}
