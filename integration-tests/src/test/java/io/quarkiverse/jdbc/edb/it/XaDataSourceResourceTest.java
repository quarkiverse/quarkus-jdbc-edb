package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that a datasource using XA transactions can reach the database.
 * <p>
 * These assertions pass in JVM mode whether or not the extension registers
 * {@code com.edb.xa.PGXADataSource} for reflection, because introspection needs no metadata there.
 * The case that matters is {@link XaDataSourceResourceIT}, which runs them against the native
 * executable: without the registration Agroal cannot see the XA DataSource's setters, applies none
 * of the configuration, and the driver falls back to {@code localhost:5432} with no credentials.
 * <p>
 * That is exactly how XA support broke in release 1.0.0, undetected, because nothing in this module
 * used an XA datasource.
 *
 * @see XaDataSourceResourceIT for the same assertions against the native executable
 */
@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class XaDataSourceResourceTest {

    private static final int COMMITTED_ID = 1;
    private static final int ROLLED_BACK_ID = 2;

    /**
     * Deliberately not a {@code @BeforeEach}: only the transactional tests need the probe table, and
     * making the others depend on it would mask which layer failed. The assertions below are ordered
     * from cheapest to most demanding, so a native run reports how far the XA path actually got.
     */
    private void resetProbeTable() {
        given()
                .when().get("/xa/reset")
                .then().statusCode(200);
    }

    @Test
    public void xaDataSourceUsesTheEdbXaDataSourceClass() {
        given()
                .when().get("/xa/provider-class")
                .then().statusCode(200)
                .body(is("com.edb.xa.PGXADataSource"));
    }

    @Test
    public void xaDataSourceIsServedByTheEdbDriver() {
        given()
                .when().get("/xa/connection-class")
                .then().statusCode(200)
                .body(startsWith("com.edb."));
    }

    /**
     * The configured URL is a {@code jdbc:edb:} one. A connection made from dropped configuration
     * would report the driver's default instead, so this fails even in the case where something
     * happens to be listening on the fallback port.
     */
    @Test
    public void xaDataSourceConnectsWithTheConfiguredUrl() {
        given()
                .when().get("/xa/connection-url")
                .then().statusCode(200)
                .body(startsWith("jdbc:edb:"));
    }

    @Test
    public void committedWorkIsVisibleAfterwards() {
        resetProbeTable();

        given()
                .when().get("/xa/commit/" + COMMITTED_ID)
                .then().statusCode(200);

        given()
                .when().get("/xa/count/" + COMMITTED_ID)
                .then().statusCode(200)
                .body(is("1"));
    }

    @Test
    public void rolledBackWorkLeavesNothingBehind() {
        resetProbeTable();

        given()
                .when().get("/xa/rollback/" + ROLLED_BACK_ID)
                .then().statusCode(500);

        given()
                .when().get("/xa/count/" + ROLLED_BACK_ID)
                .then().statusCode(200)
                .body(is("0"));
    }
}
