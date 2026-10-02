package com.lehnade.mbia.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;

/**
 * Architecture rules of the Mbia backend, from {@code mbia-specs/technical/architecture.md} §3–8
 * and ADR-001.
 *
 * <ol>
 *   <li>{@code domain} depends on nothing framework-specific (Spring, JPA, Jackson, AWS) and not
 *       on {@code api} or {@code infrastructure} (§5, §8).
 *   <li>{@code application} does not depend on {@code api} or {@code infrastructure} (§5).
 *   <li>{@code api} does not depend on {@code infrastructure} (§5, §7).
 *   <li>A module does not depend on another module's {@code domain}, {@code api} or
 *       {@code infrastructure}; only its {@code application} package and the {@code shared}
 *       module are allowed (§3, ADR-001).
 *   <li>No application-wide {@code controller}, {@code service}, {@code repository} or
 *       {@code entity} package (§3).
 *   <li>JPA {@code @Entity} classes live in {@code infrastructure} (§8).
 * </ol>
 *
 * <p>Each rule takes the root package so that it can be proved against violating fixtures (see
 * {@code ArchitectureRulesViolationTest}). Rules allow an empty "should" because most modules
 * are still empty.
 */
final class ArchitectureRules {

    static final String ROOT = "com.lehnade.mbia";

    private static final String SHARED_MODULE = "shared";
    private static final Set<String> MODULE_PRIVATE_LAYERS = Set.of("domain", "api", "infrastructure");

    private ArchitectureRules() {}

    static ArchRule domainIsFrameworkFree(String root) {
        return noClasses()
                .that().resideInAPackage(root + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..",
                        "software.amazon..",
                        root + "..api..",
                        root + "..infrastructure..")
                .as("1. domain does not depend on Spring, JPA, Jackson, AWS, api or infrastructure")
                .allowEmptyShould(true);
    }

    static ArchRule applicationDoesNotDependOnApiOrInfrastructure(String root) {
        return noClasses()
                .that().resideInAPackage(root + "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + "..api..",
                        root + "..infrastructure..")
                .as("2. application does not depend on api or infrastructure")
                .allowEmptyShould(true);
    }

    static ArchRule apiDoesNotDependOnInfrastructure(String root) {
        return noClasses()
                .that().resideInAPackage(root + "..api..")
                .should().dependOnClassesThat().resideInAPackage(root + "..infrastructure..")
                .as("3. api does not depend on infrastructure")
                .allowEmptyShould(true);
    }

    static ArchRule modulesOnlyUseOtherModulesApplicationOrShared(String root) {
        return classes()
                .that().resideInAPackage(root + "..")
                .should(notDependOnAnotherModulesPrivateLayers(root))
                .as("4. a module only depends on another module's application package or on shared")
                .allowEmptyShould(true);
    }

    static ArchRule noTechnicalTopLevelPackages(String root) {
        return noClasses()
                .should().resideInAnyPackage(
                        root + ".controller..",
                        root + ".service..",
                        root + ".repository..",
                        root + ".entity..")
                .as("5. no top-level controller, service, repository or entity package")
                .allowEmptyShould(true);
    }

    static ArchRule jpaEntitiesLiveInInfrastructure(String root) {
        return classes()
                .that().resideInAPackage(root + "..")
                .and().areAnnotatedWith("jakarta.persistence.Entity")
                .should().resideInAPackage(root + "..infrastructure..")
                .as("6. JPA @Entity classes live in infrastructure")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> notDependOnAnotherModulesPrivateLayers(String root) {
        return new ArchCondition<>("not depend on another module's domain, api or infrastructure") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> originModule = moduleOf(root, origin.getPackageName());
                if (originModule.isEmpty()) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    Optional<String> targetModule = moduleOf(root, targetPackage);
                    Optional<String> targetLayer = layerOf(root, targetPackage);
                    boolean violation = targetModule.isPresent()
                            && !targetModule.get().equals(originModule.get())
                            && !targetModule.get().equals(SHARED_MODULE)
                            && targetLayer.filter(MODULE_PRIVATE_LAYERS::contains).isPresent();
                    if (violation) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** The module is the first package segment below the root, e.g. {@code family}. */
    private static Optional<String> moduleOf(String root, String packageName) {
        return segment(root, packageName, 0);
    }

    /** The layer is the second package segment below the root, e.g. {@code domain}. */
    private static Optional<String> layerOf(String root, String packageName) {
        return segment(root, packageName, 1);
    }

    private static Optional<String> segment(String root, String packageName, int index) {
        if (!packageName.startsWith(root + ".")) {
            return Optional.empty();
        }
        String[] segments = packageName.substring(root.length() + 1).split("\\.");
        return index < segments.length ? Optional.of(segments[index]) : Optional.empty();
    }
}
