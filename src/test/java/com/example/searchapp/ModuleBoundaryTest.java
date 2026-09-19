package com.example.searchapp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.example.searchapp",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {
  @ArchTest
  static final ArchRule onboardingDoesNotDependOnSearch =
      noClasses()
          .that()
          .resideInAPackage("..onboarding..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..search..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule searchDoesNotDependOnOnboarding =
      noClasses()
          .that()
          .resideInAPackage("..search..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..onboarding..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule onboardingExceptionsLiveAtTheModuleRoot =
      classes()
          .that()
          .areAssignableTo(RuntimeException.class)
          .and()
          .resideInAPackage("..onboarding..")
          .should()
          .resideInAPackage("..onboarding.exception");
}
