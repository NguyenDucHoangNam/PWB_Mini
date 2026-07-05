package com.pwb.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

@AnalyzeClasses(packages = "com.pwb.backend", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureConventionsTests {

  @ArchTest
  static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest
  static final ArchRule no_access_to_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest
  static final ArchRule no_generic_exceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  @ArchTest
  static final ArchRule controllers_should_be_suffixed_and_in_controller_package =
      classes()
          .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
          .or().areAnnotatedWith(org.springframework.stereotype.Controller.class)
          .should().haveSimpleNameEndingWith("Controller")
          .andShould().resideInAPackage("..controller..");

  @ArchTest
  static final ArchRule services_should_be_suffixed_and_in_service_package =
      classes()
          .that().resideInAPackage("..service..")
          .and().areNotInterfaces()
          .should().haveSimpleNameEndingWith("Service")
          .orShould().haveSimpleNameEndingWith("ServiceImpl");

  @ArchTest
  static final ArchRule repositories_should_be_suffixed_and_be_interfaces =
      classes()
          .that().areAssignableTo(org.springframework.data.repository.Repository.class)
          .should().haveSimpleNameEndingWith("Repository")
          .andShould().beInterfaces();
}