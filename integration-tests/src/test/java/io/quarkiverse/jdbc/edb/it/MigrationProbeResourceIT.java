package io.quarkiverse.jdbc.edb.it;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions as {@link MigrationProbeResourceTest} against the native executable, where
 * both migration tools depend on classpath resources and reflection that a native image does not keep
 * by default.
 */
@QuarkusIntegrationTest
@WithTestResource(EdbDatabaseTestResource.class)
public class MigrationProbeResourceIT extends MigrationProbeResourceTest {
}
