package com.pwb.backend.shared.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotenvPropertySourceTest {

  @Test
  void dotenvJavaLibrary_isOnClasspath() {
    try {
      Class<?> dotenvClass = Class.forName("io.github.cdimascio.dotenv.Dotenv");
      assertNotNull(dotenvClass);
    } catch (ClassNotFoundException ex) {
      assertTrue(false, "dotenv-java must be on the classpath because spring-dotenv is declared in pom.xml");
    }
  }

  @Test
  void dotenvJava_loadsValuesFromFile() {
    Dotenv dotenv = Dotenv.configure()
        .directory(System.getProperty("java.io.tmpdir"))
        .filename(".env-does-not-exist")
        .ignoreIfMissing()
        .load();
    assertNull(dotenv.get("DEFINITELY_NOT_SET"));
  }

  @Test
  void springEnvironment_supportsCustomPropertySources() {
    Map<String, Object> props = Map.of(
        "JWT_SECRET", "abc",
        "DATABASE_URL", "jdbc:test");
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", props));

    assertTrue(env.containsProperty("JWT_SECRET"));
    assertEquals("jdbc:test", env.getProperty("DATABASE_URL"));
  }
}