package io.quarkiverse.jdbc.edb.it;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions as {@link XaDataSourceResourceTest} against the native executable, where
 * the XA connection path depends on reflection metadata that JVM mode does not need.
 */
@QuarkusIntegrationTest
@WithTestResource(EdbDatabaseTestResource.class)
public class XaDataSourceResourceIT extends XaDataSourceResourceTest {
}
