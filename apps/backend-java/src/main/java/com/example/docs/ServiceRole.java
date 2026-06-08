package com.example.docs;

public enum ServiceRole {
  AUTH,
  DOCUMENT,
  REALTIME,
  ALL;

  private static final java.util.Set<String> VALID = java.util.Set.of("auth", "document", "realtime", "all");

  public static ServiceRole from(String value) {
    if (value == null || value.isBlank()) {
      return ALL;
    }
    var normalized = value.trim().toLowerCase();
    if (!VALID.contains(normalized)) {
      throw new IllegalArgumentException(
          "Invalid app.service-role '" + value + "'. Must be one of: auth, document, realtime, all.");
    }
    return switch (normalized) {
      case "auth" -> AUTH;
      case "document" -> DOCUMENT;
      case "realtime" -> REALTIME;
      default -> ALL;
    };
  }

  public boolean servesAuth() {
    return this == AUTH || this == ALL;
  }

  public boolean servesDocument() {
    return this == DOCUMENT || this == ALL;
  }

  public boolean servesRealtime() {
    return this == REALTIME || this == ALL;
  }

  public boolean servesWebSocket() {
    return this == REALTIME || this == ALL;
  }
}
