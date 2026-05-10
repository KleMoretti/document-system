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
  WebMvcConfigurer cors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("*")
            .allowedHeaders("Authorization", "Content-Type")
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS");
      }
    };
  }
}
