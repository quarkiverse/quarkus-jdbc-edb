package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JdbcEdbResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/jdbc-edb")
                .then()
                .statusCode(200)
                .body(is("Hello jdbc-edb"));
    }
}
