package io.quarkiverse.jdbc.edb.it;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions as {@link TlsProbeResourceTest} against the native executable, which is the
 * mode that matters: TLS in a native image depends on {@code ExtensionSslNativeSupportBuildItem} and on
 * the driver's {@code SecureRandom} holder being initialised at runtime rather than captured in the
 * image heap. Neither is exercised by JVM mode.
 */
@QuarkusIntegrationTest
// Skipped under -Pepas. That profile means "run against a real EPAS instance", and these tests
// start a PostgreSQL container of their own regardless -- which cannot work where -Pepas is
// normally used, since Testcontainers has no Docker access inside the EPAS container. TLS
// against a real EPAS instance is deliberately out of scope here; it belongs to the internal
// EPAS verification suite.
@DisabledIfSystemProperty(named = "edb.jdbc.url", matches = ".+")
// The TLS container serves only the 'tls' datasource. The application's other datasources -- the
// default one that Hibernate needs, and the Flyway and Liquibase ones that migrate at start --
// still need a database, so the ordinary resource runs alongside it.
@WithTestResource(EdbDatabaseTestResource.class)
@WithTestResource(TlsDatabaseTestResource.class)
public class TlsProbeResourceIT extends TlsProbeResourceTest {
}
