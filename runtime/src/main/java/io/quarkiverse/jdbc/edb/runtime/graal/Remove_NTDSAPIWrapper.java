package io.quarkiverse.jdbc.edb.runtime.graal;

import com.edb.sspi.NTDSAPIWrapper;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Deletes {@code com.edb.sspi.NTDSAPIWrapper} from native images. Its methods are declared to throw
 * {@code com.sun.jna.LastErrorException}, so the class cannot be loaded without JNA on the
 * classpath. It is only reachable from SSPI, which {@link DisableSSPIClient} already removes.
 */
@TargetClass(NTDSAPIWrapper.class)
@Delete
public final class Remove_NTDSAPIWrapper {
}
