package io.quarkiverse.jdbc.edb.it;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates the certificate material the TLS tests need, into a temporary directory.
 * <p>
 * Generating rather than committing avoids three problems: private keys in a public repository trip
 * secret scanners, committed certificates expire, and {@code openssl} on macOS is LibreSSL and does
 * not always behave like the OpenSSL on a CI runner.
 * <p>
 * There is a bonus specific to this driver. The EDB driver requires the client key in <em>PKCS#8
 * DER</em> and cannot read a PEM key -- users have to run
 * {@code openssl pkcs8 -topk8 -outform DER} to convert one. {@link java.security.PrivateKey#getEncoded()}
 * already returns exactly that encoding, so here it is simply written out as bytes.
 */
final class TestCertificates {

    /**
     * PostgreSQL's {@code cert} authentication method maps the client certificate's common name to the
     * database user, so this is both the certificate CN and the role the TLS tests connect as.
     */
    static final String MTLS_USER = "mtlsuser";

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;
    private static final Duration VALIDITY = Duration.ofDays(365);

    private final Path directory;

    private TestCertificates(Path directory) {
        this.directory = directory;
    }

    /**
     * @return the material for one test run: a CA, a server certificate valid for {@code localhost}, a
     *         client certificate for {@link #MTLS_USER}, and a second unrelated CA used to prove that
     *         verification actually rejects an untrusted chain.
     */
    static TestCertificates generate() {
        try {
            Path directory = Files.createTempDirectory("jdbc-edb-tls");
            directory.toFile().deleteOnExit();
            TestCertificates certificates = new TestCertificates(directory);
            certificates.write();
            return certificates;
        } catch (IOException | GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException("Could not generate TLS test certificates", e);
        }
    }

    Path directory() {
        return directory;
    }

    Path caCertificate() {
        return directory.resolve("ca.crt");
    }

    Path serverCertificate() {
        return directory.resolve("server.crt");
    }

    Path serverKey() {
        return directory.resolve("server.key");
    }

    Path clientCertificate() {
        return directory.resolve("client.crt");
    }

    /**
     * The client key in PKCS#8 DER, which is the only form the EDB driver accepts.
     */
    Path clientKey() {
        return directory.resolve("client.key.pk8");
    }

    /**
     * A CA that signed nothing in use here. Pointing {@code sslrootcert} at it must make
     * {@code verify-full} fail, which is what proves the setting is enforced rather than merely accepted.
     */
    Path untrustedCaCertificate() {
        return directory.resolve("untrusted-ca.crt");
    }

    Path hbaFile() {
        return directory.resolve("pg_hba.conf");
    }

    private void write() throws IOException, GeneralSecurityException, OperatorCreationException {
        KeyPair caKeyPair = keyPair();
        X509Certificate ca = certificate(caKeyPair, "CN=jdbc-edb-test-ca", caKeyPair, "CN=jdbc-edb-test-ca", true, null);
        writePem(caCertificate(), ca);

        KeyPair serverKeyPair = keyPair();
        X509Certificate server = certificate(serverKeyPair, "CN=localhost", caKeyPair, "CN=jdbc-edb-test-ca", false,
                "localhost");
        writePem(serverCertificate(), server);
        writePem(serverKey(), serverKeyPair.getPrivate());

        KeyPair clientKeyPair = keyPair();
        X509Certificate client = certificate(clientKeyPair, "CN=" + MTLS_USER, caKeyPair, "CN=jdbc-edb-test-ca", false,
                null);
        writePem(clientCertificate(), client);
        Files.write(clientKey(), clientKeyPair.getPrivate().getEncoded());

        KeyPair untrustedKeyPair = keyPair();
        X509Certificate untrusted = certificate(untrustedKeyPair, "CN=untrusted-ca", untrustedKeyPair, "CN=untrusted-ca",
                true, null);
        writePem(untrustedCaCertificate(), untrusted);

        Files.writeString(hbaFile(), hbaContent());
    }

    /**
     * Rules the TLS tests depend on, in order. The {@link #MTLS_USER} entries come first so that they
     * win over the permissive catch-alls: a plaintext connection as that user is rejected outright, and
     * an encrypted one must present a client certificate. Scoping the strictness to a single role is
     * what lets one container serve both the mutual-TLS tests and the ordinary ones.
     */
    private static String hbaContent() {
        return """
                local     all all              trust
                hostnossl all %1$s all         reject
                hostssl   all %1$s all         cert
                hostssl   all all all          trust
                host      all all all          trust
                """.formatted(MTLS_USER);
    }

    private static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(KeyPair subjectKeyPair, String subject, KeyPair issuerKeyPair,
            String issuer, boolean certificateAuthority, String subjectAlternativeName)
            throws GeneralSecurityException, OperatorCreationException, IOException {

        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Principal(issuer),
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(VALIDITY)),
                new X500Principal(subject),
                subjectKeyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(certificateAuthority));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(subjectKeyPair.getPublic()));

        if (subjectAlternativeName != null) {
            // sslmode=verify-full checks the hostname against this, and Testcontainers publishes the
            // database on localhost. Without it, verify-full fails where verify-ca would pass.
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, subjectAlternativeName)));
        }

        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKeyPair.getPrivate())));
    }

    private static void writePem(Path path, Object object) {
        try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
            writer.writeObject(object);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }
}
