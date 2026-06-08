package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BearerTokenVerifierTest {
  private final JwtManager jwtManager = new JwtManager("test-secret", Duration.ofHours(1));
  private final BearerTokenVerifier verifier = new BearerTokenVerifier(jwtManager);

  @Test
  void verifiesValidToken() {
    var token = jwtManager.sign(new UserClaims("user-1", "ada@example.com"));
    var claims = verifier.claims("Bearer " + token);

    assertThat(claims.userId()).isEqualTo("user-1");
    assertThat(claims.email()).isEqualTo("ada@example.com");
  }

  @Test
  void rejectsMissingAuthorization() {
    assertThatThrownBy(() -> verifier.claims(null))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Missing bearer token.");
  }

  @Test
  void rejectsNonBearerAuthorization() {
    assertThatThrownBy(() -> verifier.claims("Basic dXNlcjpwYXNz"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Missing bearer token.");
  }

  @Test
  void rejectsInvalidToken() {
    assertThatThrownBy(() -> verifier.claims("Bearer invalid"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid bearer token.");
  }

  @Test
  void rejectsExpiredToken() {
    var expiredManager = new JwtManager("test-secret", Duration.ofSeconds(-1));
    var expiredVerifier = new BearerTokenVerifier(expiredManager);
    var token = expiredManager.sign(new UserClaims("user-1", "ada@example.com"));

    assertThatThrownBy(() -> expiredVerifier.claims("Bearer " + token))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid bearer token.");
  }

  @Test
  void rejectsTamperedToken() {
    var token = jwtManager.sign(new UserClaims("user-1", "ada@example.com"));

    assertThatThrownBy(() -> verifier.claims("Bearer " + token + "x"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid bearer token.");
  }
}
