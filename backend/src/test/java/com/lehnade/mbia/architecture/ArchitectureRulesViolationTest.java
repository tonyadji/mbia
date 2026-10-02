package com.lehnade.mbia.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Proves that every {@link ArchitectureRules} rule detects violations, so that no rule can
 * silently become a no-op. Each rule is checked against its own fixture root in
 * {@code com.lehnade.archfixtures.ruleN}, which contains violating classes and compliant ones.
 */
class ArchitectureRulesViolationTest {

    private static final String FIXTURES = "com.lehnade.archfixtures";

    @Test
    void rule1_domainDependingOnFrameworksApiOrInfrastructureIsDetected() {
        String report = violationReport("rule1", ArchitectureRules::domainIsFrameworkFree);

        assertThat(report)
                .contains("DomainUsingSpring", "org.springframework.stereotype.Component")
                .contains("DomainUsingJpa", "jakarta.persistence.Id")
                .contains("DomainUsingJackson", "com.fasterxml.jackson.annotation.JsonProperty")
                .contains("DomainUsingApi")
                .contains("DomainUsingInfrastructure")
                .doesNotContain("CompliantDomain");
    }

    @Test
    void rule2_applicationDependingOnApiOrInfrastructureIsDetected() {
        String report = violationReport("rule2", ArchitectureRules::applicationDoesNotDependOnApiOrInfrastructure);

        assertThat(report)
                .contains("ApplicationUsingApi")
                .contains("ApplicationUsingInfrastructure")
                .doesNotContain("CompliantApplication");
    }

    @Test
    void rule3_apiDependingOnInfrastructureIsDetected() {
        String report = violationReport("rule3", ArchitectureRules::apiDoesNotDependOnInfrastructure);

        assertThat(report)
                .contains("ApiUsingInfrastructure")
                .doesNotContain("CompliantApi");
    }

    @Test
    void rule4_moduleDependingOnAnotherModulesDomainApiOrInfrastructureIsDetected() {
        String report = violationReport("rule4", ArchitectureRules::modulesOnlyUseOtherModulesApplicationOrShared);

        assertThat(report)
                .contains("UsesFamilyDomain")
                .contains("UsesFamilyApi")
                .contains("UsesFamilyInfrastructure")
                .doesNotContain("UsesFamilyApplicationAndShared");
    }

    @Test
    void rule5_topLevelTechnicalPackagesAreDetected() {
        String report = violationReport("rule5", ArchitectureRules::noTechnicalTopLevelPackages);

        assertThat(report)
                .contains(FIXTURES + ".rule5.controller.FamilyController")
                .contains(FIXTURES + ".rule5.service.FamilyService")
                .contains(FIXTURES + ".rule5.repository.FamilyRepository")
                .contains(FIXTURES + ".rule5.entity.FamilyEntity")
                .doesNotContain(FIXTURES + ".rule5.family.api.FamilyController");
    }

    @Test
    void rule6_jpaEntityOutsideInfrastructureIsDetected() {
        String report = violationReport("rule6", ArchitectureRules::jpaEntitiesLiveInInfrastructure);

        assertThat(report)
                .contains("EntityInDomain")
                .doesNotContain("FamilyJpaEntity");
    }

    /** Checks the rule against the fixture root and returns the failure report it must produce. */
    private static String violationReport(String fixture, Function<String, ArchRule> rule) {
        String root = FIXTURES + "." + fixture;
        JavaClasses classes = new ClassFileImporter().importPackages(root);

        Throwable failure = catchThrowable(() -> rule.apply(root).check(classes));

        assertThat(failure)
                .as("rule must fail on fixture %s", root)
                .isInstanceOf(AssertionError.class);
        return failure.getMessage();
    }
}
