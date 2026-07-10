package com.pwb.backend.shared.security;

import javax.crypto.SecretKey;

public interface TokenKeyProvider {

  SecretKey currentSigningKey();

  int MIN_KEY_BYTES = 32;
}
