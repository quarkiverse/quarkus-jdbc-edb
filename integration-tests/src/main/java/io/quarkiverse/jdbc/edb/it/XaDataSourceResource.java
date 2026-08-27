package io.quarkiverse.jdbc.edb.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;

/**
 * Exposes a datasource configured with {@code jdbc.transactions=xa} over HTTP.
 * <p>
 * Agroal configures an XA datasource by <em>JavaBean introspection</em> of
 * {@code com.edb.xa.PGXADataSource}, rather than by handing the JDBC URL to the driver as it does on
 * the plain driver path. In a native image the setters are only visible to {@link
 * java.beans.Introspector} if the class was registered for reflection at build time. Without that
 * registration Agroal finds no properties at all, applies none of the configuration -- the URL
 * included -- and the driver silently falls back to its own defaults.
 * <p>
 * That failure exists only in a native image, so these assertions matter through
 * {@link XaDataSourceResourceIT}. They are routed over HTTP for the same reason as
 * {@link NamedDataSourceResource}: {@code @QuarkusIntegrationTest} runs against the executable out
 * of process, where injection is unavailable.
 */
@Path("/xa")
@Produces(MediaType.TEXT_PLAIN)
public class XaDataSourceResource {

    private static final String PROBE_TABLE = "xa_probe";

    @Inject
    @DataSource("xa")
    AgroalDataSource xaDataSource;

    /**
     * The class Agroal instantiates to obtain connections. This is resolved at build time from
     * {@code jdbc.transactions=xa}, so it reports the XA DataSource whether or not reflection
     * metadata was registered -- which is precisely what makes it useful: if this passes while the
     * connection assertions fail, the datasource is configured correctly and the fault is in the
     * native image's reflection metadata rather than in the test setup.
     */
    @GET
    @Path("/provider-class")
    public String providerClass() {
        return xaDataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .connectionProviderClass()
                .getName();
    }

    /**
     * The implementation class of the driver's own connection. Reaching this at all requires Agroal
     * to have applied the configured URL and credentials to the XA DataSource.
     */
    @GET
    @Path("/connection-class")
    public String connectionClass() throws SQLException {
        try (Connection connection = xaDataSource.getConnection()) {
            return connection.unwrap(Connection.class).getClass().getName();
        }
    }

    /**
     * The URL the driver actually connected with, which is not necessarily the one that was
     * configured. When the configuration is dropped the driver defaults to {@code localhost:5432},
     * so asserting on this distinguishes "connected to the intended database" from "connected to
     * something".
     */
    @GET
    @Path("/connection-url")
    public String connectionUrl() throws SQLException {
        try (Connection connection = xaDataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }

    /**
     * Creates the probe table if it is absent and empties it, so that a run is unaffected by rows
     * left behind by an earlier one. Deliberately not transactional: it must take effect regardless
     * of what the transactional endpoints below go on to do.
     */
    @GET
    @Path("/reset")
    public String reset() throws SQLException {
        try (Connection connection = xaDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + PROBE_TABLE + " (id INTEGER PRIMARY KEY)");
            statement.executeUpdate("DELETE FROM " + PROBE_TABLE);
        }
        return "ok";
    }

    @GET
    @Path("/commit/{id}")
    @Transactional
    public String commit(@PathParam("id") int id) throws SQLException {
        insert(id);
        return "ok";
    }

    /**
     * Inserts a row and then fails, so that the transaction manager rolls the XA branch back. Paired
     * with {@link #count(int)} this proves the resource was genuinely enlisted in the transaction,
     * rather than the insert having been committed by an auto-commit connection.
     */
    @GET
    @Path("/rollback/{id}")
    @Transactional
    public String rollback(@PathParam("id") int id) throws SQLException {
        insert(id);
        throw new IllegalStateException("deliberate rollback");
    }

    @GET
    @Path("/count/{id}")
    public String count(@PathParam("id") int id) throws SQLException {
        try (Connection connection = xaDataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT count(*) FROM " + PROBE_TABLE + " WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? String.valueOf(rows.getInt(1)) : "no rows";
            }
        }
    }

    private void insert(int id) throws SQLException {
        try (Connection connection = xaDataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("INSERT INTO " + PROBE_TABLE + " (id) VALUES (?)")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
