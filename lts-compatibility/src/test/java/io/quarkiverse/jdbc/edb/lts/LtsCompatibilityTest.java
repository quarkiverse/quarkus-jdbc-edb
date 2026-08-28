package io.quarkiverse.jdbc.edb.lts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies the extension works on the oldest Quarkus version it declares support for.
 * <p>
 * The value is mostly in getting here at all: reaching these assertions means Quarkus completed
 * augmentation, which is where it loads the extension's deployment module and executes every
 * {@code @BuildStep} against this version's classes. A build step calling an API added after the
 * declared floor fails there, exactly as it would in a user's own build. The assertions below then
 * confirm the results of those build steps actually took effect rather than silently doing nothing.
 * <p>
 * If this fails while the main build passes, the extension has adopted a Quarkus API newer than the
 * floor declared by {@code requiresQuarkusCore}. Either stop using that API, or raise the floor
 * deliberately -- and update the pinned version here to match.
 */
@QuarkusTest
public class LtsCompatibilityTest {

    @Test
    public void driverIsResolvedFromTheExtension() {
        given()
                .when().get("/lts/driver")
                .then().statusCode(200)
                .body(is("com.edb.Driver"));
    }

    @Test
    public void connectionConfigurerFromTheExtensionIsApplied() {
        given()
                .when().get("/lts/exception-sorter")
                .then().statusCode(200)
                .body(is("PostgreSQLExceptionSorter"));
    }
}
