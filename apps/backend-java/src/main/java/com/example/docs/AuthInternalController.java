package com.example.docs;

import java.util.Arrays;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * AUTH role requires SERVICE_TOKEN at startup. ALL role can start without it,
 * but token-gated lookup endpoints will reject every request until configured.
 */
@RestController
@RequestMapping("/internal")
@ConditionalOnRole({ServiceRole.AUTH, ServiceRole.ALL})
public class AuthInternalController {
  private static final Logger log = LoggerFactory.getLogger(AuthInternalController.class);
  private final AuthRepository repository;
  private final String serviceToken;

  public AuthInternalController(
      AuthRepository repository,
      ServiceRole role,
      @Value("${app.service-token:}") String serviceToken) {
    this.repository = repository;
    this.serviceToken = serviceToken;
    if (serviceToken == null || serviceToken.isBlank()) {
      if (role == ServiceRole.ALL) {
        log.warn("SERVICE_TOKEN is blank — internal endpoints will reject all requests. "
            + "Set SERVICE_TOKEN if you need internal user lookups from another service.");
      } else {
        throw new IllegalStateException(
            "SERVICE_TOKEN must be set when running in auth role.");
      }
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
    if (serviceToken == null || serviceToken.isBlank()) {
      throw new ForbiddenException("Internal endpoints are not available — SERVICE_TOKEN is not configured.");
    }
    if (token == null || token.isBlank()) {
      throw new ForbiddenException("Missing X-Service-Token header.");
    }
    if (!serviceToken.equals(token)) {
      throw new ForbiddenException("Invalid service token.");
    }
  }
}
