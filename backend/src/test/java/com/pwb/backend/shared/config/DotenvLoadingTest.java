package com.pwb.backend.shared.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotenvLoadingTest {

  @Test
  void dotenv_loadsFromWorkingDirectory() throws Exception {
    File tempDir = Files.createTempDirectory("dotenv-test").toFile();
    File envFile = new File(tempDir, ".env");
    try (FileWriter writer = new FileWriter(envFile)) {
      writer.write("TEST_KEY_FROM_DOTENV=hello-from-dotenv\n");
      writer.write("JWT_SECRET=test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa\n");
    }

    Dotenv dotenv = Dotenv.configure()
        .directory(tempDir.getAbsolutePath())
        .filename(".env")
        .load();

    assertEquals("hello-from-dotenv", dotenv.get("TEST_KEY_FROM_DOTENV"));
    assertEquals("test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa", dotenv.get("JWT_SECRET"));

    assertTrue(envFile.delete());
    assertTrue(tempDir.delete());
  }

  @Test
  void dotenv_missingFile_returnsEmptyDotenv() throws Exception {
    File tempDir = Files.createTempDirectory("dotenv-empty-test").toFile();

    Dotenv dotenv = Dotenv.configure()
        .directory(tempDir.getAbsolutePath())
        .filename(".env-does-not-exist")
        .ignoreIfMissing()
        .load();

    org.junit.jupiter.api.Assertions.assertNull(dotenv.get("ANY_KEY"));

    assertTrue(tempDir.delete());
  }

  @Test
  void dotenv_jwtSecretFromEnv_meetsLengthRequirement() throws Exception {
    File tempDir = Files.createTempDirectory("dotenv-jwt-test").toFile();
    File envFile = new File(tempDir, ".env");
    String secret = "this-is-a-very-long-secret-with-more-than-32-bytes-1234";
    try (FileWriter writer = new FileWriter(envFile)) {
      writer.write("JWT_SECRET=" + secret + "\n");
    }

    Dotenv dotenv = Dotenv.configure()
        .directory(tempDir.getAbsolutePath())
        .filename(".env")
        .load();

    String loaded = dotenv.get("JWT_SECRET");
    assertEquals(secret, loaded);
    assertTrue(loaded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32,
        "JWT_SECRET must be at least 32 bytes for HS256");

    assertTrue(envFile.delete());
    assertTrue(tempDir.delete());
  }
}