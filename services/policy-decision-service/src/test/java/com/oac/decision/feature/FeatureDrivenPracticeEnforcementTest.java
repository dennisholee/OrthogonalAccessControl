package com.oac.decision.feature;

import com.oac.decision.bdd.PolicyDecisionFeatureTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureDrivenPracticeEnforcementTest {

    @Test
    void featureDirectoryMustContainAtLeastOneFeatureFile() throws IOException {
        Path featureDir = Path.of("src/test/resources/features");
        assertThat(Files.exists(featureDir)).isTrue();

        try (Stream<Path> files = Files.list(featureDir)) {
            List<Path> featureFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".feature"))
                    .toList();
            assertThat(featureFiles).isNotEmpty();
        }
    }

    @Test
    void policyFeatureMustUseFeatureDrivenTag() throws IOException {
        Path policyFeature = Path.of("src/test/resources/features/policy-decision.feature");
        assertThat(Files.exists(policyFeature)).isTrue();

        String content = Files.readString(policyFeature);
        assertThat(content).contains("@feature-driven");
    }

    @Test
    void cucumberSuiteRunnerMustExistAndTargetCucumberEngine() {
        Class<?> runner = PolicyDecisionFeatureTest.class;
        assertThat(runner.isAnnotationPresent(Suite.class)).isTrue();

        IncludeEngines includeEngines = runner.getAnnotation(IncludeEngines.class);
        assertThat(includeEngines).isNotNull();
        assertThat(includeEngines.value()).contains("cucumber");
    }
}
