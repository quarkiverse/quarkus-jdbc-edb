package io.quarkiverse.jdbc.edb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.kubernetes.service.binding.runtime.ServiceBinding;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConfigSource;

/**
 * Exercises {@link EdbServiceBindingConverter} through its public API. A Service Binding is a
 * directory of files whose names are property names, so these tests build one on disk rather than
 * mocking, which also covers the {@code sslrootcert} path resolution against the binding directory.
 */
public class EdbServiceBindingConverterTest {

    private static ServiceBindingConfigSource convert(Path bindingDirectory) {
        Optional<ServiceBindingConfigSource> source = new EdbServiceBindingConverter()
                .convert(List.of(new ServiceBinding(bindingDirectory)));
        assertTrue(source.isPresent(), "Expected the converter to match the binding");
        return source.get();
    }

    private static void write(Path directory, String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content);
    }

    private static void writeBaseBinding(Path directory) throws IOException {
        write(directory, "type", "edb");
        write(directory, "host", "epas.example.com");
        write(directory, "port", "5444");
        write(directory, "database", "edb");
        write(directory, "username", "enterprisedb");
        write(directory, "password", "secret");
    }

    @Test
    public void buildsJdbcEdbUrlAndCredentials(@TempDir Path binding) throws IOException {
        writeBaseBinding(binding);

        ServiceBindingConfigSource source = convert(binding);

        // The jdbc:edb: prefix is what makes binding type "edb" the correct choice: the EDB driver
        // rejects jdbc:postgresql: URLs.
        assertEquals("jdbc:edb://epas.example.com:5444/edb",
                source.getValue("quarkus.datasource.jdbc.url"));
        assertEquals("enterprisedb", source.getValue("quarkus.datasource.username"));
        assertEquals("secret", source.getValue("quarkus.datasource.password"));
    }

    @Test
    public void omitsPortWhenNotBound(@TempDir Path binding) throws IOException {
        write(binding, "type", "edb");
        write(binding, "host", "epas.example.com");
        write(binding, "database", "edb");

        ServiceBindingConfigSource source = convert(binding);

        assertEquals("jdbc:edb://epas.example.com/edb", source.getValue("quarkus.datasource.jdbc.url"));
    }

    @Test
    public void appendsSslModeAlone(@TempDir Path binding) throws IOException {
        writeBaseBinding(binding);
        write(binding, "sslmode", "require");

        ServiceBindingConfigSource source = convert(binding);

        assertEquals("jdbc:edb://epas.example.com:5444/edb?sslmode=require",
                source.getValue("quarkus.datasource.jdbc.url"));
    }

    @Test
    public void appendsSslRootCertResolvedAgainstBindingDirectory(@TempDir Path binding) throws IOException {
        writeBaseBinding(binding);
        write(binding, "sslmode", "verify-full");
        write(binding, "sslrootcert", "root.crt");

        ServiceBindingConfigSource source = convert(binding);

        String separator = FileSystems.getDefault().getSeparator();
        assertEquals("jdbc:edb://epas.example.com:5444/edb"
                + "?sslmode=verify-full&sslrootcert=" + binding + separator + "root.crt",
                source.getValue("quarkus.datasource.jdbc.url"));
    }

    @Test
    public void ignoresBindingsOfAnotherType(@TempDir Path binding) throws IOException {
        writeBaseBinding(binding);
        write(binding, "type", "postgresql");

        Optional<ServiceBindingConfigSource> source = new EdbServiceBindingConverter()
                .convert(List.of(new ServiceBinding(binding)));

        assertTrue(source.isEmpty(), "A postgresql binding must not be claimed by the EDB converter");
    }
}
