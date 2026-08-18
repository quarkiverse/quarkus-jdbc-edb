package io.quarkiverse.jdbc.edb.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.edb.Driver;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that {@code quarkus.datasource.db-kind} can be omitted entirely, which is what the
 * {@code DefaultDataSourceDbKindBuildItem} produced by the extension enables. Note that an
 * explicitly configured db-kind is returned by Quarkus without consulting that build item, so this
 * test is the only one that exercises it.
 */
public class JdbcEdbImplicitDbKindTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> ShrinkWrap.create(JavaArchive.class))
            // Deliberately unreachable -- see the note in JdbcEdbXaDataSourceTest.
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:edb://localhost:1/edb")
            .overrideConfigKey("quarkus.datasource.username", "edb")
            .overrideConfigKey("quarkus.datasource.password", "edb")
            .overrideConfigKey("quarkus.datasource.jdbc.min-size", "0")
            .overrideConfigKey("quarkus.datasource.health.enabled", "false")
            // See the note in JdbcEdbXaDataSourceTest: keep Hibernate out of this test so it needs
            // no database.
            .overrideConfigKey("quarkus.hibernate-orm.enabled", "false");

    @Inject
    AgroalDataSource dataSource;

    @Test
    public void dbKindIsResolvedImplicitly() {
        assertNotNull(dataSource);
        assertEquals(Driver.class, dataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .connectionProviderClass());
    }
}
