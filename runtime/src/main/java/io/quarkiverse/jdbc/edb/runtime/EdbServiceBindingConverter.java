package io.quarkiverse.jdbc.edb.runtime;

import java.nio.file.FileSystems;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.kubernetes.service.binding.runtime.DatasourceServiceBindingConfigSourceFactory;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBinding;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConfigSource;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConverter;

/**
 * Maps a Service Binding of type {@code edb} to datasource configuration, so credentials projected
 * by the Service Binding Operator configure the datasource without further configuration.
 * <p>
 * Deliberately simpler than the PostgreSQL equivalent: it handles {@code sslmode} and
 * {@code sslrootcert} but omits the CockroachDB {@code options} handling, which is not applicable
 * to EDB Postgres Advanced Server.
 */
public class EdbServiceBindingConverter implements ServiceBindingConverter {

    public static final String BINDING_TYPE = "edb";
    public static final String SSL_MODE = "sslmode";
    public static final String SSL_ROOT_CERT = "sslrootcert";

    @Override
    public Optional<ServiceBindingConfigSource> convert(List<ServiceBinding> serviceBindings) {
        return ServiceBinding.singleMatchingByType(BINDING_TYPE, serviceBindings)
                .map(new EdbDatasourceServiceBindingConfigSourceFactory());
    }

    private static class EdbDatasourceServiceBindingConfigSourceFactory
            extends DatasourceServiceBindingConfigSourceFactory.Jdbc {

        @Override
        protected String formatUrl(String urlFormat, String type, String host, String database, String portPart) {
            String result = super.formatUrl(urlFormat, type, host, database, portPart);

            Map<String, String> properties = serviceBinding.getProperties();
            String sslMode = properties.getOrDefault(SSL_MODE, "");
            String sslRootCert = properties.getOrDefault(SSL_ROOT_CERT, "");

            StringBuilder params = new StringBuilder();
            if (!sslMode.isEmpty()) {
                params.append(SSL_MODE).append("=").append(sslMode);
            }
            if (!sslRootCert.isEmpty()) {
                if (params.length() > 0) {
                    params.append("&");
                }
                params.append(SSL_ROOT_CERT).append("=")
                        .append(serviceBinding.getBindingDirectory())
                        .append(FileSystems.getDefault().getSeparator())
                        .append(sslRootCert);
            }

            return params.length() > 0 ? result + "?" + params : result;
        }
    }
}
