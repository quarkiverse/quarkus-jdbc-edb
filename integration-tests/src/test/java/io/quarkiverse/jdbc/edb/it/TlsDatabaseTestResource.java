package io.quarkiverse.jdbc.edb.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Starts a PostgreSQL container with TLS enabled, for {@link TlsProbeResourceTest}.
 * <p>
 * Separate from {@link EdbDatabaseTestResource} because it needs server-side configuration that the
 * other tests must not inherit -- in particular a {@code pg_hba.conf} that demands a client
 * certificate. Scoping that demand to a single role keeps one container able to serve both the mutual
 * TLS tests and the ordinary ones.
 * <p>
 * Everything here runs against community PostgreSQL. What is under test is the driver's and the
 * extension's TLS handling -- {@code ExtensionSslNativeSupportBuildItem}, certificate loading, and the
 * runtime-initialised {@code SecureRandom} -- none of which is EPAS-specific, so it belongs in CI
 * rather than in the internal EPAS suite.
 */
public class TlsDatabaseTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String DATABASE = "edb";
    private static final String USERNAME = "edb";
    private static final String PASSWORD = "edb";

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        TestCertificates certificates = TestCertificates.generate();

        container = new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName(DATABASE)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withCopyToContainer(transfer(certificates.serverCertificate(), 0644), "/certs/server.crt")
                .withCopyToContainer(transfer(certificates.serverKey(), 0600), "/certs/server.key")
                .withCopyToContainer(transfer(certificates.caCertificate(), 0644), "/certs/ca.crt")
                .withCopyToContainer(transfer(certificates.hbaFile(), 0644), "/certs/pg_hba.conf")
                // PostgreSQL requires the key to be owned by the postgres user: root ownership is
                // accepted only at 0640, and postgres is not in the root group. Nothing Testcontainers
                // copies in can be owned by postgres, so fix it in a wrapper that hands off to the
                // image's own entrypoint. This works because the container starts as root.
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh", "-c",
                        "chown postgres:postgres /certs/server.key && exec docker-entrypoint.sh \"$@\"", "sh"))
                .withCommand("postgres",
                        "-c", "ssl=on",
                        "-c", "ssl_cert_file=/certs/server.crt",
                        "-c", "ssl_key_file=/certs/server.key",
                        "-c", "ssl_ca_file=/certs/ca.crt",
                        "-c", "hba_file=/certs/pg_hba.conf");
        container.start();

        createMutualTlsRole();

        String baseUrl = toEdbUrl(container.getJdbcUrl());

        Map<String, String> config = new HashMap<>();
        // Consumed by TlsProbeResource to build each scenario's URL. Paths are on the host filesystem,
        // which the application -- JVM or native binary -- shares with this test.
        config.put("tls.probe.base-url", baseUrl);
        config.put("tls.probe.username", USERNAME);
        config.put("tls.probe.password", PASSWORD);
        config.put("tls.probe.mtls-username", TestCertificates.MTLS_USER);
        config.put("tls.probe.ca", certificates.caCertificate().toString());
        config.put("tls.probe.untrusted-ca", certificates.untrustedCaCertificate().toString());
        config.put("tls.probe.client-certificate", certificates.clientCertificate().toString());
        config.put("tls.probe.client-key", certificates.clientKey().toString());

        // The one datasource wired through Agroal and the extension, at the strongest tier. The
        // scenario endpoints cover the rest through DriverManager, which keeps this application from
        // carrying a pool per TLS variation.
        config.put("quarkus.datasource.\"tls\".jdbc.url", mutualTlsUrl(baseUrl, certificates));
        config.put("quarkus.datasource.\"tls\".username", TestCertificates.MTLS_USER);

        return config;
    }

    private static String mutualTlsUrl(String baseUrl, TestCertificates certificates) {
        return baseUrl + "&sslmode=verify-full"
                + "&sslrootcert=" + certificates.caCertificate()
                + "&sslcert=" + certificates.clientCertificate()
                + "&sslkey=" + certificates.clientKey();
    }

    /**
     * The role the mutual TLS tests authenticate as. It needs no password: {@code pg_hba.conf} gives it
     * the {@code cert} method, which authenticates from the client certificate's common name alone.
     */
    private void createMutualTlsRole() {
        try (Connection connection = DriverManager.getConnection(toEdbUrl(container.getJdbcUrl()), USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + TestCertificates.MTLS_USER + " LOGIN");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the mutual TLS role", e);
        }
    }

    private static Transferable transfer(Path path, int mode) {
        try {
            return Transferable.of(Files.readAllBytes(path), mode);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    private static String toEdbUrl(String jdbcUrl) {
        return jdbcUrl.replaceFirst("^jdbc:postgresql:", "jdbc:edb:");
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
