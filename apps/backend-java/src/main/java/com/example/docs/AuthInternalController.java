package com.example.docs;

import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoints used by document service to resolve user info.
 * Secured by X-Service-Token header, not JWT.
 * SERVICE_TOKEN must be set; startup fails otherwise regardless of role.
 */
@RestController
@RequestMapping("/internal")
@ConditionalOnRole({ServiceRole.AUTH, ServiceRole.ALL})
public class AuthInternalController {
  private final AuthRepository repository;
  private final String serviceToken;

  public AuthInternalController(
      AuthRepository repository,
      @Value("${app.service-token:}") String serviceToken) {
    this.repository = repository;
    this.serviceToken = serviceToken;
    if (serviceToken == null || serviceToken.isBlank()) {
      throw new IllegalStateException(
          "SERVICE_TOKEN must be set. Internal endpoints are always token-gated.");
    }
  }

  @GetMapping("/users/by-email")
  User getUserByEmail(
      @RequestHeader("X-Service-Token") String token,
      @RequestParam("email") String email) {
    verifyToken(token);
    if (email == null || email.isBlank()) {
      throw new BadRequestException("Email is required.");
    }
    try {
      return repository.findUserByEmail(email);
    } catch (EmptyResultDataAccessException ex) {
      throw new UserNotFoundException("User not found.");
    }
  }

  @GetMapping("/users")
  Map<String, User> getUsersByIds(
      @RequestHeader("X-Service-Token") String token,
      @RequestParam("ids") String ids) {
    verifyToken(token);
    if (ids == null || ids.isBlank()) {
      return Map.of();
    }
    var idList = Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(id -> !id.isBlank())
        .toList();
    return repository.findUsersByIds(idList);
  }

  @GetMapping("/healthz")
  Map<String, String> internalHealthz() {
    return Map.of("status", "ok");
  }

  private void verifyToken(String token) {
    if (!serviceToken.equals(token)) {
      throw new ForbiddenException("Invalid service token.");
    }
  }
}
