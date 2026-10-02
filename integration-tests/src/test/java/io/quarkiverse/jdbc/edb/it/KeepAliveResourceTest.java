package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@code EdbAgroalConnectionConfigurer.setKeepAlive}/{@code setReadTimeout} actually run,
 * restored when the build moved to Quarkus 3.40 after being dropped for the 3.33 LTS pin (both
 * methods were added to {@code AgroalConnectionConfigurer} after 3.33). A build-success check alone
 * would not catch a regression here: Quarkus calls these through a default method and logs a warning
 * rather than failing when a database kind doesn't implement them, so the failure mode is silent.
 *
 * @see KeepAliveResourceIT for the same assertions against the native executable
 */
@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class KeepAliveResourceTest {

    @Test
    public void keepAliveIsAppliedToTheDriverConnection() {
        given()
                .when().get("/keep-alive/tcp-keep-alive")
                .then().statusCode(200)
                .body(is("true"));
    }

    @Test
    public void readTimeoutIsAppliedToTheDriverConnection() {
        given()
                .when().get("/keep-alive/socket-timeout")
                .then().statusCode(200)
                .body(is("45"));
    }
}
