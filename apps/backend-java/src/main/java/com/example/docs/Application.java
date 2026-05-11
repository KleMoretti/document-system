package com.example.docs;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import redis.clients.jedis.JedisPooled;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtManager jwtManager(@Value("${app.jwt-secret}") String secret) {
    if (secret == null || secret.isBlank() || "change-this-development-secret".equals(secret)) {
      throw new IllegalStateException("JWT_SECRET must be set to a non-default value.");
    }
    return new JwtManager(secret, Duration.ofHours(24));
  }

  @Bean
  JedisPooled jedis(
      @Value("${app.redis-host}") String host,
      @Value("${app.redis-port}") int port,
      @Value("${app.redis-password}") String password) {
    if (password == null || password.isBlank()) {
      return new JedisPooled(host, port);
    }
    return new JedisPooled(host, port, null, password);
  }

  @Bean
  WebMvcConfigurer cors(@Value("${app.allowed-origins}") String allowedOrigins) {
    var origins =
        java.util.Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins(origins)
            .allowedHeaders("Authorization", "Content-Type")
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS");
      }
    };
  }
}
