package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthControllerTest {
  @Test
  void invalidBearerTokenIsUnauthorized() {
    var controller =
        new AuthController(null, null, new JwtManager("test-secret", Duration.ofHours(1)));

    assertThatThrownBy(() -> controller.claims("Bearer invalid"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid bearer token.");
  }

  @Test
  void unknownLoginEmailUsesSameUnauthorizedErrorAsWrongPassword() {
    var repository = mock(AppRepository.class);
    when(repository.findUserForLogin("missing@example.com"))
        .thenThrow(new EmptyResultDataAccessException(1));
    var controller =
        new AuthController(
            repository,
            new BCryptPasswordEncoder(),
            new JwtManager("test-secret", Duration.ofHours(1)));

    assertThatThrownBy(
            () -> controller.login(new LoginRequest("missing@example.com", "password123")))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Email or password is incorrect.");
  }
}
