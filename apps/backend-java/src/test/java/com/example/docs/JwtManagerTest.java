package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtManagerTest {
  @Test
  void signsAndVerifiesToken() {
    var manager = new JwtManager("test-secret", Duration.ofHours(1));

    var token = manager.sign(new UserClaims("user-1", "ada@example.com"));
    var claims = manager.verify(token);

    assertThat(claims.userId()).isEqualTo("user-1");
    assertThat(claims.email()).isEqualTo("ada@example.com");
  }

  @Test
  void rejectsTamperedToken() {
    var manager = new JwtManager("test-secret", Duration.ofHours(1));
    var token = manager.sign(new UserClaims("user-1", "ada@example.com"));

    assertThatThrownBy(() -> manager.verify(token + "x")).isInstanceOf(IllegalArgumentException.class);
  }
}
