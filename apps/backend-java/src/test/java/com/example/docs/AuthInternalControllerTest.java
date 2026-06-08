package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthInternalControllerTest {
  @Test
  void rejectsEmptyServiceToken() {
    var repo = new AuthRepository(mock(JdbcTemplate.class));
    var role = ServiceRole.ALL;
    var controller = new AuthInternalController(repo, role, "");

    assertThatThrownBy(() -> controller.getUserByEmail("", "ada@example.com"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("SERVICE_TOKEN is not configured");
  }

  @Test
  void rejectsMissingXServiceTokenHeader() {
    var repo = new AuthRepository(mock(JdbcTemplate.class));
    var role = ServiceRole.AUTH;
    var controller = new AuthInternalController(repo, role, "secret");

    assertThatThrownBy(() -> controller.getUserByEmail("", "ada@example.com"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Missing X-Service-Token header.");
  }

  @Test
  void rejectsWrongServiceToken() {
    var repo = new AuthRepository(mock(JdbcTemplate.class));
    var role = ServiceRole.AUTH;
    var controller = new AuthInternalController(repo, role, "secret");

    assertThatThrownBy(() -> controller.getUserByEmail("wrong", "ada@example.com"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Invalid service token.");
  }

  @Test
  void internalHealthzDoesNotRequireToken() {
    var repo = new AuthRepository(mock(JdbcTemplate.class));
    var role = ServiceRole.ALL;
    var controller = new AuthInternalController(repo, role, "");

    var result = controller.internalHealthz();
    org.assertj.core.api.Assertions.assertThat(result).containsEntry("status", "ok");
  }

  @Test
  void authRoleRejectsEmptyServiceTokenAtStartup() {
    var repo = new AuthRepository(mock(JdbcTemplate.class));
    var role = ServiceRole.AUTH;

    assertThatThrownBy(() -> new AuthInternalController(repo, role, ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SERVICE_TOKEN must be set when running in auth role.");
  }
}
