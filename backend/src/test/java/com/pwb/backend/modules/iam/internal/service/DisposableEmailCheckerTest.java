package com.pwb.backend.modules.iam.internal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposableEmailCheckerTest {

  private DisposableEmailChecker checker;

  @BeforeEach
  void setUp() {
    checker = new DisposableEmailChecker();
  }

  @Test
  void testIsDisposable_withDisposableEmail_returnsTrue() {
    assertTrue(checker.isDisposable("test@tempmail.com"));
    assertTrue(checker.isDisposable("user@10minutemail.com"));
    assertTrue(checker.isDisposable("admin@yopmail.com"));
  }

  @Test
  void testIsDisposable_withValidEmail_returnsFalse() {
    assertFalse(checker.isDisposable("nam@gmail.com"));
    assertFalse(checker.isDisposable("hoang@yahoo.com"));
    assertFalse(checker.isDisposable("admin@pwbmini.com"));
  }

  @Test
  void testIsDisposable_withNullOrEmpty_returnsFalse() {
    assertFalse(checker.isDisposable(null));
    assertFalse(checker.isDisposable(""));
    assertFalse(checker.isDisposable("   "));
  }
}
