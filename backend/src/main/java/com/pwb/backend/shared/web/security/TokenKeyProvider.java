package com.pwb.backend.shared.web.security;

import javax.crypto.SecretKey;

public interface TokenKeyProvider {

  SecretKey currentSigningKey();

  int MIN_KEY_BYTES = 32;
}
