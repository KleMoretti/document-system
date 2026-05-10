package com.example.docs;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AppRepository repository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JwtManager jwtManager;

  public AuthController(
      AppRepository repository, BCryptPasswordEncoder passwordEncoder, JwtManager jwtManager) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.jwtManager = jwtManager;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthResponse register(@RequestBody RegisterRequest req) {
    if (blank(req.email()) || blank(req.password()) || blank(req.displayName())) {
      throw new BadRequestException("Email, password and displayName are required.");
    }
    var user =
        repository.createUser(
            req.email(), passwordEncoder.encode(req.password()), req.displayName());
    return new AuthResponse(jwtManager.sign(new UserClaims(user.id(), user.email())), user);
  }

  @PostMapping("/auth/login")
  AuthResponse login(@RequestBody LoginRequest req) {
    var user = repository.findUserForLogin(req.email());
    if (!passwordEncoder.matches(req.password(), user.passwordHash())) {
      throw new UnauthorizedException("Email or password is incorrect.");
    }
    var apiUser = new User(user.id(), user.email(), user.displayName(), user.createdAt());
    return new AuthResponse(jwtManager.sign(new UserClaims(user.id(), user.email())), apiUser);
  }

  @GetMapping("/me")
  User me(@RequestHeader("Authorization") String authorization) {
    var claims = claims(authorization);
    return repository.findUser(claims.userId());
  }

  UserClaims claims(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new UnauthorizedException("Missing bearer token.");
    }
    return jwtManager.verify(authorization.substring("Bearer ".length()));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  @ExceptionHandler(DuplicateKeyException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiError duplicate() {
    return new ApiError("USER_EXISTS", "User already exists.");
  }
}
