package com.example.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the auth service's internal endpoints to resolve user info.
 * Used by the document service when users table is in a separate database.
 */
public class AuthInternalClient {
  private static final Logger log = LoggerFactory.getLogger(AuthInternalClient.class);
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final String baseUrl;
  private final String serviceToken;

  public AuthInternalClient(String baseUrl, String serviceToken) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.serviceToken = serviceToken;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    this.mapper = new ObjectMapper();
  }

  public User findUserByEmail(String email) {
    try {
      var req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/internal/users/by-email?email=" + urlEncode(email)))
          .header("X-Service-Token", serviceToken)
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 200) {
        return mapper.readValue(resp.body(), User.class);
      }
      if (resp.statusCode() == 404) {
        throw new UserNotFoundException("User not found.");
      }
      log.warn("Auth internal /users/by-email returned {}", resp.statusCode());
      throw new RuntimeException("Auth internal lookup failed: " + resp.statusCode());
    } catch (UserNotFoundException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Auth internal /users/by-email call failed", ex);
      throw new RuntimeException("Could not resolve user by email.", ex);
    }
  }

  public Map<String, User> findUsersByIds(List<String> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    try {
      var joined = String.join(",", ids);
      var req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/internal/users?ids=" + urlEncode(joined)))
          .header("X-Service-Token", serviceToken)
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 200) {
        var node = mapper.readTree(resp.body());
        var builder = new java.util.HashMap<String, User>();
        var fields = node.fields();
        while (fields.hasNext()) {
          var entry = fields.next();
          builder.put(entry.getKey(), mapper.treeToValue(entry.getValue(), User.class));
        }
        return builder;
      }
      log.warn("Auth internal /users returned {}", resp.statusCode());
      return Map.of();
    } catch (Exception ex) {
      log.error("Auth internal /users call failed", ex);
      return Map.of();
    }
  }

  private String urlEncode(String value) {
    try {
      return java.net.URLEncoder.encode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }
}
