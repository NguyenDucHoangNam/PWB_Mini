package com.pwb.backend.iam.internal.interfaces.security;

import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import com.pwb.backend.shared.web.security.JwtSigner;
import com.pwb.backend.shared.web.security.TokenKeyProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * IAM-side implementation of {@link TokenKeyProvider}: reads the symmetric
 * secret from {@link IamProperties} and validates length at startup.
 *
 * <p>Pulled out of the old {@code JwtService} so {@code shared.security}
 * components can verify tokens without depending on IAM internals.
 */
@Component
@RequiredArgsConstructor
public class JwtKeyProvider implements TokenKeyProvider {

  private final IamProperties iamProperties;

  @PostConstruct
  void validateSecret() {
    String secret = iamProperties.getJwt().getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET is required but not set. Set the JWT_SECRET environment variable "
              + "with at least " + MIN_KEY_BYTES + " bytes (256 bits) of random data.");
    }
    int byteLength = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (byteLength < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must be at least " + MIN_KEY_BYTES + " bytes (256 bits). "
              + "Current length: " + byteLength + " bytes.");
    }
  }

  @Override
  public SecretKey currentSigningKey() {
    return JwtSigner.toHmacKey(iamProperties.getJwt().getSecret());
  }
}
