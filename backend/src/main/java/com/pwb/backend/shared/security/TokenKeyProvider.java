package com.pwb.backend.shared.security;

import javax.crypto.SecretKey;

/**
 * Abstraction over the symmetric key used to sign and verify JWTs.
 *
 * <p>Resolves the {@code shared.security} primitive dependency on the
 * concrete {@code IamProperties} configuration. Implementations live in the
 * owning IAM module and bind to whatever secret source (KMS, env var,
 * rotation store) is appropriate.
 */
public interface TokenKeyProvider {

  /**
   * Returns the currently active signing key. Implementations may rotate
   * internally; callers must re-invoke on every token operation rather than
   * caching the key.
   */
  SecretKey currentSigningKey();

  /** Minimum acceptable key length in bytes (HMAC-SHA256). */
  int MIN_KEY_BYTES = 32;
}
