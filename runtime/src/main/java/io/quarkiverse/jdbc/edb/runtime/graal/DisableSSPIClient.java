package io.quarkiverse.jdbc.edb.runtime.graal;

import com.edb.core.PGStream;
import com.edb.core.v3.ConnectionFactoryImpl;
import com.edb.sspi.ISSPIClient;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Removes SSPI (Windows integrated authentication) support from native images.
 * <p>
 * {@code com.edb.sspi.SSPIClient} needs JNA, which arrives through the {@code waffle-jna}
 * dependency that {@code com.enterprisedb:edb-jdbc} declares as optional -- so it is normally absent.
 * The driver reaches SSPIClient through a {@code Class.forName} in {@code createSSPI}, which
 * GraalVM constant-folds and then tries to initialise at image build time, failing with
 * {@code NoClassDefFoundError: com/sun/jna/LastErrorException}. Substituting the factory method
 * keeps SSPIClient out of the analysis graph entirely.
 * <p>
 * Note the EDB driver declares {@code createSSPI} as an <em>instance</em> method, unlike upstream
 * pgjdbc where it is static. The substitution must match, or it silently fails to apply.
 */
@TargetClass(ConnectionFactoryImpl.class)
public final class DisableSSPIClient {

    @Substitute
    private ISSPIClient createSSPI(PGStream pgStream, String spnServiceClass, boolean enableNegotiate) {
        throw new IllegalStateException("com.edb.sspi.SSPIClient is not available in GraalVM native images");
    }
}
