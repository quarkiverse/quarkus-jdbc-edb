package io.quarkiverse.jdbc.edb.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class JdbcEdbProcessor {

    private static final String FEATURE = "jdbc-edb";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
