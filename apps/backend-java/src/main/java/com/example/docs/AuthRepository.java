package com.example.docs;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnRole({ServiceRole.AUTH, ServiceRole.ALL})
public class AuthRepository {
  private final JdbcTemplate jdbc;

  public AuthRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public User createUser(String email, String passwordHash, String displayName) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
        id, email, passwordHash, displayName);
    return findUser(id);
  }

  public LoginUser findUserForLogin(String email) {
    return jdbc.queryForObject(
        "SELECT id, email, display_name, created_at, password_hash FROM users WHERE email = ?",
        (rs, row) ->
            new LoginUser(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("password_hash")),
        email);
  }

  public User findUser(String id) {
    return jdbc.queryForObject(
        "SELECT id, email, display_name, created_at FROM users WHERE id = ?",
        (rs, row) ->
            new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant()),
        id);
  }

  public User findUserByEmail(String email) {
    return jdbc.queryForObject(
        "SELECT id, email, display_name, created_at FROM users WHERE email = ?",
        (rs, row) ->
            new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant()),
        email);
  }

  public Map<String, User> findUsersByIds(List<String> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    var placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
    var results = jdbc.query(
        "SELECT id, email, display_name, created_at FROM users WHERE id IN (" + placeholders + ")",
        (rs, row) ->
            new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant()),
        ids.toArray());
    return results.stream().collect(Collectors.toMap(User::id, u -> u));
  }

  public void ping() {
    jdbc.queryForObject("SELECT 1", Integer.class);
  }

  public record LoginUser(
      String id, String email, String displayName, java.time.Instant createdAt, String passwordHash) {}
}
