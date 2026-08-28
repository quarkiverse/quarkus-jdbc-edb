package io.quarkiverse.jdbc.edb.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.Config;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;

/**
 * Reports what the server says about the transport security of a connection.
 * <p>
 * Every assertion here is made <em>server-side</em>, from {@code pg_stat_ssl}, rather than from the
 * client's own configuration. Asserting that a URL contained {@code sslmode=verify-full} would prove
 * only that the property was set; asking the server whether the session is actually encrypted, with
 * which protocol, and which client certificate it authenticated, proves it took effect.
 * <p>
 * Configuration is read at request time rather than injected, so that this resource imposes nothing on
 * the application when the other test classes -- which do not start the TLS container -- run.
 */
@Path("/tls")
@Produces(MediaType.TEXT_PLAIN)
public class TlsProbeResource {

    private static final String SSL_QUERY = "SELECT ssl, version, cipher, client_dn FROM pg_stat_ssl WHERE pid = pg_backend_pid()";

    @Inject
    Config config;

    /**
     * The datasource wired through Agroal and the extension, configured for mutual TLS. This is the
     * path a real application takes, and the one that would break if the extension stopped contributing
     * {@code ExtensionSslNativeSupportBuildItem}.
     */
    @Inject
    @DataSource("tls")
    AgroalDataSource tlsDataSource;

    @GET
    @Path("/datasource")
    public String datasource() throws SQLException {
        try (Connection connection = tlsDataSource.getConnection()) {
            return describe(connection);
        }
    }

    /**
     * Connects with the settings for one named scenario and reports the outcome.
     * <p>
     * A connection the server refuses is a <em>result</em> here, not an error: the negative scenarios
     * exist to show that TLS is enforced rather than merely requested, so a refusal is reported as
     * {@code REFUSED: ...} with HTTP 200. That keeps a genuine fault in this endpoint, which would
     * surface as a 500, distinguishable from the rejection the test is looking for.
     */
    @GET
    @Path("/scenario/{name}")
    public String scenario(@PathParam("name") String name) {
        String baseUrl = property("tls.probe.base-url");
        String ca = property("tls.probe.ca");
        String username;
        String url;

        switch (name) {
            case "require" -> {
                // Encrypts without validating the server certificate.
                username = property("tls.probe.username");
                url = baseUrl + "&sslmode=require";
            }
            case "verify-full" -> {
                // Additionally validates the chain and the hostname.
                username = property("tls.probe.username");
                url = baseUrl + "&sslmode=verify-full&sslrootcert=" + ca;
            }
            case "mutual" -> {
                username = property("tls.probe.mtls-username");
                url = baseUrl + "&sslmode=verify-full&sslrootcert=" + ca
                        + "&sslcert=" + property("tls.probe.client-certificate")
                        + "&sslkey=" + property("tls.probe.client-key");
            }
            case "untrusted-ca" -> {
                // Same as verify-full, but trusting a CA that signed nothing in use. Must fail, or
                // verify-full is being accepted and ignored.
                username = property("tls.probe.username");
                url = baseUrl + "&sslmode=verify-full&sslrootcert=" + property("tls.probe.untrusted-ca");
            }
            case "missing-client-certificate" -> {
                // Encrypted, but presenting no client certificate as the role pg_hba.conf requires one
                // from. Must be refused by the server.
                username = property("tls.probe.mtls-username");
                url = baseUrl + "&sslmode=verify-full&sslrootcert=" + ca;
            }
            case "plaintext" -> {
                // Unencrypted as the mutual TLS role, which pg_hba.conf rejects outright.
                username = property("tls.probe.mtls-username");
                url = baseUrl + "&sslmode=disable";
            }
            default -> throw new IllegalArgumentException("Unknown TLS scenario: " + name);
        }

        try (Connection connection = DriverManager.getConnection(url, username, property("tls.probe.password"))) {
            return describe(connection);
        } catch (SQLException e) {
            return "REFUSED: " + e.getMessage();
        }
    }

    private static String describe(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(SSL_QUERY)) {
            if (!rows.next()) {
                return "no pg_stat_ssl row";
            }
            return "ssl=" + rows.getBoolean("ssl")
                    + ";version=" + nullSafe(rows.getString("version"))
                    + ";cipher=" + nullSafe(rows.getString("cipher"))
                    + ";client_dn=" + nullSafe(rows.getString("client_dn"));
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String property(String name) {
        return config.getValue(name, String.class);
    }
}
