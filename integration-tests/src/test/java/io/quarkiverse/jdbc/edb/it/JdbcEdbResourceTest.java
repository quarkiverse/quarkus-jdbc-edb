package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class JdbcEdbResourceTest {

    @Test
    public void opensAConnection() {
        given()
                .when().get("/jdbc-edb")
                .then().statusCode(200)
                .body(not(emptyString()));
    }

    @Test
    public void connectionIsServedByTheEdbDriver() {
        // A com.edb.* connection implementation proves com.edb.Driver handled the URL. This is
        // checked instead of getDriverName(), which is unreliable for a pgjdbc fork.
        given()
                .when().get("/jdbc-edb/connection-class")
                .then().statusCode(200)
                .body(startsWith("com.edb."));
    }

    @Test
    public void urlUsesTheEdbPrefix() {
        // The EDB driver rejects jdbc:postgresql: URLs, so this also confirms the prefix rewrite in
        // EdbDatabaseTestResource is applied.
        given()
                .when().get("/jdbc-edb/url")
                .then().statusCode(200)
                .body(startsWith("jdbc:edb:"));
    }
}
