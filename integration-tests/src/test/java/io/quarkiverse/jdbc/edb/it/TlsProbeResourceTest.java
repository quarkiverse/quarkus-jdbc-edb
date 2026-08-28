package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies encrypted connections at each level the driver offers, and that each is enforced.
 * <p>
 * Every positive assertion is paired with a negative one. "The connection succeeded with
 * {@code sslmode=verify-full}" on its own proves nothing -- a driver that silently ignored the setting
 * would pass it. What makes it evidence is that the same connection is <em>refused</em> when the
 * certificate authority is wrong. The same reasoning applies to the client certificate and to
 * plaintext.
 * <p>
 * All of this runs against community PostgreSQL, so it runs in CI on every commit. TLS against a real
 * EPAS instance is separate work and belongs to the internal EPAS suite.
 *
 * @see TlsProbeResourceIT for the same assertions against the native executable
 */
@QuarkusTest
// The TLS container serves only the 'tls' datasource. The application's other datasources -- the
// default one that Hibernate needs, and the Flyway and Liquibase ones that migrate at start --
// still need a database, so the ordinary resource runs alongside it.
@WithTestResource(EdbDatabaseTestResource.class)
@WithTestResource(TlsDatabaseTestResource.class)
public class TlsProbeResourceTest {

    @Test
    public void dataSourceConnectionIsEncryptedAndMutuallyAuthenticated() {
        given()
                .when().get("/tls/datasource")
                .then().statusCode(200)
                .body(startsWith("ssl=true"))
                .body(containsString("client_dn=/CN=" + TestCertificates.MTLS_USER));
    }

    @Test
    public void requireEncryptsTheConnection() {
        given()
                .when().get("/tls/scenario/require")
                .then().statusCode(200)
                .body(startsWith("ssl=true"))
                .body(containsString("version=TLSv1."));
    }

    @Test
    public void verifyFullEncryptsAndValidatesTheServerCertificate() {
        given()
                .when().get("/tls/scenario/verify-full")
                .then().statusCode(200)
                .body(startsWith("ssl=true"));
    }

    @Test
    public void mutualTlsAuthenticatesWithTheClientCertificate() {
        given()
                .when().get("/tls/scenario/mutual")
                .then().statusCode(200)
                .body(startsWith("ssl=true"))
                .body(containsString("client_dn=/CN=" + TestCertificates.MTLS_USER));
    }

    /**
     * Proves {@code verify-full} actually validates the chain. Without this, the positive case above
     * would also pass against a driver that accepted the property and ignored it.
     */
    @Test
    public void verifyFullRejectsAnUntrustedCertificateAuthority() {
        given()
                .when().get("/tls/scenario/untrusted-ca")
                .then().statusCode(200)
                .body(startsWith("REFUSED"))
                .body(containsString("PKIX"));
    }

    /**
     * Proves the server enforces the client certificate rather than the client merely offering one.
     */
    @Test
    public void mutualTlsRoleIsRefusedWithoutAClientCertificate() {
        given()
                .when().get("/tls/scenario/missing-client-certificate")
                .then().statusCode(200)
                .body(startsWith("REFUSED"))
                .body(containsString("client certificate"));
    }

    /**
     * Proves encryption is required, not merely available: {@code pg_hba.conf} rejects an unencrypted
     * connection for this role.
     */
    @Test
    public void mutualTlsRoleIsRefusedOverAnUnencryptedConnection() {
        given()
                .when().get("/tls/scenario/plaintext")
                .then().statusCode(200)
                .body(startsWith("REFUSED"))
                .body(containsString("pg_hba.conf"));
    }
}
