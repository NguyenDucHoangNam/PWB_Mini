package com.pwb.backend.iam.internal.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposableEmailCheckerTest {

  @Test
  void testIsDisposable_withDisposableEmail_returnsTrue() {
    assertTrue(DisposableEmailChecker.isDisposable("test@tempmail.com"));
    assertTrue(DisposableEmailChecker.isDisposable("user@10minutemail.com"));
    assertTrue(DisposableEmailChecker.isDisposable("admin@yopmail.com"));
  }

  @Test
  void testIsDisposable_withValidEmail_returnsFalse() {
    assertFalse(DisposableEmailChecker.isDisposable("nam@gmail.com"));
    assertFalse(DisposableEmailChecker.isDisposable("hoang@yahoo.com"));
    assertFalse(DisposableEmailChecker.isDisposable("admin@pwbmini.com"));
  }

  @Test
  void testIsDisposable_withNullOrEmpty_returnsFalse() {
    assertFalse(DisposableEmailChecker.isDisposable(null));
    assertFalse(DisposableEmailChecker.isDisposable(""));
    assertFalse(DisposableEmailChecker.isDisposable("   "));
  }
}
