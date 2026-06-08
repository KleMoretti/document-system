package com.example.docs;

import org.springframework.stereotype.Component;

/**
 * Shared token verification used by document and realtime services
 * to validate JWTs locally without depending on the auth service.
 */
@Component
public class BearerTokenVerifier {
  private final JwtManager jwtManager;

  public BearerTokenVerifier(JwtManager jwtManager) {
    this.jwtManager = jwtManager;
  }

  public UserClaims claims(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new UnauthorizedException("Missing bearer token.");
    }
    try {
      return jwtManager.verify(authorization.substring("Bearer ".length()));
    } catch (IllegalArgumentException ex) {
      throw new UnauthorizedException("Invalid bearer token.");
    }
  }
}
