package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
  @Test
  void invalidBearerTokenIsUnauthorized() {
    var controller =
        new AuthController(null, null, new JwtManager("test-secret", Duration.ofHours(1)));

    assertThatThrownBy(() -> controller.claims("Bearer invalid"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid bearer token.");
  }
}
