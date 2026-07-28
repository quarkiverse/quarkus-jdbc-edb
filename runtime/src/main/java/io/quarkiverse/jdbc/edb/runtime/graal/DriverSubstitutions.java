package io.quarkiverse.jdbc.edb.runtime.graal;

import java.util.Properties;

import com.edb.Driver;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Stops the driver reconfiguring the JDK logger from its own connection properties, which would
 * override the logging configuration Quarkus has already installed.
 */
@TargetClass(Driver.class)
public final class DriverSubstitutions {

    @Substitute
    private void setupLoggerFromProperties(final Properties props) {
        // Deliberately empty: leave the Quarkus logging configuration alone.
    }
}
