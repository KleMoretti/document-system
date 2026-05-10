package com.example.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtManager {
  private final byte[] secret;
  private final Duration ttl;
  private final ObjectMapper mapper = new ObjectMapper();

  public JwtManager(String secret, Duration ttl) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.ttl = ttl;
  }

  public String sign(UserClaims claims) {
    try {
      var header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      var payload =
          encodeJson(
              Map.of(
                  "sub", claims.userId(),
                  "email", claims.email(),
                  "exp", Instant.now().plus(ttl).getEpochSecond()));
      var unsigned = header + "." + payload;
      return unsigned + "." + signature(unsigned);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not sign token", ex);
    }
  }

  public UserClaims verify(String token) {
    try {
      var parts = token.split("\\.");
      if (parts.length != 3) {
        throw new IllegalArgumentException("Invalid token");
      }
      var unsigned = parts[0] + "." + parts[1];
      if (!signature(unsigned).equals(parts[2])) {
        throw new IllegalArgumentException("Invalid signature");
      }
      var payload =
          mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), JwtPayload.class);
      if (payload.exp() < Instant.now().getEpochSecond()) {
        throw new IllegalArgumentException("Token expired");
      }
      return new UserClaims(payload.sub(), payload.email());
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid token", ex);
    }
  }

  private String encodeJson(Object value) throws Exception {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(value));
  }

  private String signature(String unsigned) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
  }

  private record JwtPayload(String sub, String email, long exp) {}
}
