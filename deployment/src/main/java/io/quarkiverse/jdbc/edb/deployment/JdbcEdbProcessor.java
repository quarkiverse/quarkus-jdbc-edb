package io.quarkiverse.jdbc.edb.deployment;

import java.util.Set;

import io.quarkiverse.jdbc.edb.runtime.EdbAgroalConnectionConfigurer;
import io.quarkiverse.jdbc.edb.runtime.EdbServiceBindingConverter;
import io.quarkus.agroal.spi.JdbcDriverBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBundleBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.DatabaseKindDialectBuildItem;

class JdbcEdbProcessor {

    private static final String FEATURE = "jdbc-edb";

    /**
     * The {@code quarkus.datasource.db-kind} value handled by this extension.
     */
    private static final String DB_KIND = "edb";

    private static final String DRIVER_CLASS = "com.edb.Driver";

    private static final String XA_DATASOURCE_CLASS = "com.edb.xa.PGXADataSource";

    /**
     * Superclass of {@link #XA_DATASOURCE_CLASS}, and where every one of its setters is actually
     * declared. See {@link #registerForReflection}.
     */
    private static final String XA_DATASOURCE_SUPERCLASS = "com.edb.ds.common.BaseDataSource";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDriver(BuildProducer<JdbcDriverBuildItem> jdbcDriver) {
        jdbcDriver.produce(new JdbcDriverBuildItem(DB_KIND, DRIVER_CLASS, XA_DATASOURCE_CLASS));
    }

    /**
     * Allows {@code quarkus.datasource.db-kind} to be omitted entirely: when this extension is the
     * only JDBC driver on the classpath, the kind is resolved implicitly. An explicitly configured
     * db-kind is returned by Quarkus without consulting this build item.
     */
    @BuildStep
    void registerDefaultDbKind(BuildProducer<DefaultDataSourceDbKindBuildItem> dbKind) {
        dbKind.produce(new DefaultDataSourceDbKindBuildItem(DB_KIND));
    }

    /**
     * Hibernate ORM already ships an {@code ENTERPRISEDB} entry in {@code org.hibernate.dialect.Database}
     * which matches the {@code EnterpriseDB} product name and resolves to {@link
     * org.hibernate.dialect.PostgresPlusDialect}, so the product name is all we need to supply.
     */
    @BuildStep
    void registerHibernateDialect(BuildProducer<DatabaseKindDialectBuildItem> dialect) {
        dialect.produce(DatabaseKindDialectBuildItem.forCoreDialect(DB_KIND, "EnterpriseDB",
                Set.of("org.hibernate.dialect.PostgresPlusDialect")));
    }

    @BuildStep
    void configureAgroalConnection(BuildProducer<AdditionalBeanBuildItem> additionalBeans, Capabilities capabilities) {
        if (capabilities.isPresent(Capability.AGROAL)) {
            additionalBeans.produce(new AdditionalBeanBuildItem.Builder()
                    .addBeanClass(EdbAgroalConnectionConfigurer.class)
                    .setDefaultScope(BuiltinScope.APPLICATION.getName())
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    void registerServiceBinding(Capabilities capabilities, BuildProducer<ServiceProviderBuildItem> serviceProvider) {
        if (capabilities.isPresent(Capability.KUBERNETES_SERVICE_BINDING)) {
            serviceProvider.produce(new ServiceProviderBuildItem(
                    "io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConverter",
                    EdbServiceBindingConverter.class.getName()));
        }
    }

    @BuildStep
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitialized) {
        // Holds a SecureRandom, which must not be captured in the native image heap.
        runtimeInitialized.produce(new RuntimeInitializedClassBuildItem("com.edb.util.PasswordUtil$SecureRandomHolder"));
        // Starts a cleaner thread; deferring initialisation keeps it out of the image heap.
        runtimeInitialized.produce(new RuntimeInitializedClassBuildItem("com.edb.util.LazyCleaner"));
    }

    @BuildStep
    void nativeResources(BuildProducer<NativeImageResourceBundleBuildItem> resourceBundle,
            BuildProducer<ExtensionSslNativeSupportBuildItem> sslNativeSupport) {
        // The driver localises its error messages through these bundles. Note that the EDB driver
        // is a fork of pgjdbc and still ships them under the original org.postgresql package.
        resourceBundle.produce(new NativeImageResourceBundleBuildItem("org.postgresql.translation.messages"));
        sslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(FEATURE));
        // The java.sql.Driver service provider is registered by Agroal itself, via
        // ServiceProviderBuildItem.allProvidersFromClassPath, so it must not be registered here.
    }

    /**
     * Registers the driver and the XA DataSource for reflection, <em>including their methods</em>,
     * and -- critically -- the XA DataSource's superclass alongside it.
     * <p>
     * Agroal configures an XA datasource by <em>JavaBean introspection</em>, not by handing the JDBC
     * URL to the driver as it does on the plain driver path. {@link java.beans.Introspector} walks
     * the class hierarchy, and in a native image it sees only what was registered. Without this,
     * introspection finds nothing, Agroal logs {@code Available properties []}, applies none of the
     * configuration -- the URL included -- and the driver falls back to its defaults: port 5432, no
     * credentials.
     * <p>
     * {@link #XA_DATASOURCE_SUPERCLASS} is not optional and is the part that is easy to get wrong.
     * Quarkus emits {@code allDeclaredMethods}, never {@code allPublicMethods}, so registering a
     * class exposes only the methods it declares itself. {@code com.edb.xa.PGXADataSource} declares
     * just five -- a constructor, two {@code getXAConnection} overloads, {@code getDescription} and
     * {@code createReference}. All 106 setters, {@code setUrl} and {@code setUser} and
     * {@code setPassword} among them, are declared on {@code BaseDataSource}. Registering only the
     * subclass therefore registers no setters at all and leaves the failure exactly as it was.
     * <p>
     * JVM mode needs no metadata to introspect, so none of this is visible there.
     * {@code XaDataSourceResourceIT} in the integration tests is what pins it.
     */
    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(DRIVER_CLASS, XA_DATASOURCE_CLASS, XA_DATASOURCE_SUPERCLASS)
                .reason("The EDB JDBC driver and XA DataSource are instantiated reflectively by Agroal, "
                        + "and the XA DataSource is configured through JavaBean introspection")
                .methods(true)
                .fields(true)
                .build());
    }

}
