package com.example.docs;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  ServiceRole serviceRole(@Value("${app.service-role:all}") String raw) {
    return ServiceRole.from(raw);
  }

  @Bean
  @ConditionalOnRole({ServiceRole.AUTH, ServiceRole.ALL})
  BCryptPasswordEncoder passwordEncoder(@Value("${app.bcrypt-cost}") int bcryptCost) {
    return new BCryptPasswordEncoder(bcryptCost);
  }

  @Bean
  JwtManager jwtManager(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-ttl}") Duration ttl) {
    if (secret == null || secret.isBlank() || "change-this-development-secret".equals(secret)) {
      throw new IllegalStateException("JWT_SECRET must be set to a non-default value.");
    }
    return new JwtManager(secret, ttl);
  }

  @Bean
  @ConditionalOnRole({ServiceRole.DOCUMENT, ServiceRole.REALTIME, ServiceRole.ALL})
  JedisPooled jedis(
      @Value("${app.redis-host}") String host,
      @Value("${app.redis-port}") int port,
      @Value("${app.redis-password}") String password,
      @Value("${app.redis-tls}") boolean redisTls) {
    var builder = DefaultJedisClientConfig.builder().ssl(redisTls);
    if (password != null && !password.isBlank()) {
      builder.password(password);
    }
    return new JedisPooled(new HostAndPort(host, port), builder.build());
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
