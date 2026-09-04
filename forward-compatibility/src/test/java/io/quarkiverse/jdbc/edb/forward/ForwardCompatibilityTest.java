package io.quarkiverse.jdbc.edb.forward;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that the extension, compiled against the oldest supported Quarkus LTS, still works on the
 * newest Quarkus release.
 * <p>
 * The value is mostly in getting here at all: reaching these assertions means Quarkus completed
 * augmentation, which is where it loads the extension's deployment module and runs every
 * {@code @BuildStep} against this version's classes. The assertions below then confirm the results of
 * those build steps actually took effect rather than silently doing nothing.
 * <p>
 * If this fails while the main build passes, something the extension relies on changed between the
 * LTS it is compiled against and the current release. That is the cost of building against the LTS,
 * and this is the check that surfaces it before a user does.
 */
@QuarkusTest
public class ForwardCompatibilityTest {

    @Test
    public void driverIsResolvedFromTheExtension() {
        given()
                .when().get("/compatibility/driver")
                .then().statusCode(200)
                .body(is("com.edb.Driver"));
    }

    @Test
    public void connectionConfigurerFromTheExtensionIsApplied() {
        given()
                .when().get("/compatibility/exception-sorter")
                .then().statusCode(200)
                .body(is("PostgreSQLExceptionSorter"));
    }
}
