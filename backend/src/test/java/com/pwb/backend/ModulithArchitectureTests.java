package com.pwb.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

  @Test
  void verifyModulithStructure() {
    ApplicationModules modules = ApplicationModules.of(BackendApplication.class);
    modules.forEach(System.out::println);
    modules.verify();
  }
}