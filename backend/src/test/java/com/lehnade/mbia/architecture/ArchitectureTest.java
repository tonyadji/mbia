package com.lehnade.mbia.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Applies the {@link ArchitectureRules} to the production code. */
@AnalyzeClasses(packages = ArchitectureRules.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree =
            ArchitectureRules.domainIsFrameworkFree(ArchitectureRules.ROOT);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnApiOrInfrastructure =
            ArchitectureRules.applicationDoesNotDependOnApiOrInfrastructure(ArchitectureRules.ROOT);

    @ArchTest
    static final ArchRule apiDoesNotDependOnInfrastructure =
            ArchitectureRules.apiDoesNotDependOnInfrastructure(ArchitectureRules.ROOT);

    @ArchTest
    static final ArchRule modulesOnlyUseOtherModulesApplicationOrShared =
            ArchitectureRules.modulesOnlyUseOtherModulesApplicationOrShared(ArchitectureRules.ROOT);

    @ArchTest
    static final ArchRule noTechnicalTopLevelPackages =
            ArchitectureRules.noTechnicalTopLevelPackages(ArchitectureRules.ROOT);

    @ArchTest
    static final ArchRule jpaEntitiesLiveInInfrastructure =
            ArchitectureRules.jpaEntitiesLiveInInfrastructure(ArchitectureRules.ROOT);
}
