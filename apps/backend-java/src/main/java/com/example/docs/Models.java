package com.example.docs;

import java.time.Instant;

record User(String id, String email, String displayName, Instant createdAt) {}

record UserClaims(String userId, String email) {}

record DocumentView(
    String id, String title, String ownerId, String role, Instant createdAt, Instant updatedAt) {}

record ShareView(String userId, String email, String displayName, String role) {}

record RegisterRequest(String email, String password, String displayName) {}

record LoginRequest(String email, String password) {}

record AuthResponse(String token, User user) {}

record CreateDocumentRequest(String title) {}

record RenameDocumentRequest(String title) {}

record ShareDocumentRequest(String email, String role) {}

record ApiError(String code, String message) {}

record WsMessage(
    String type,
    String docId,
    String userId,
    String displayName,
    String color,
    String update,
    java.util.List<String> updates,
    String code,
    String message) {}

record RedisEnvelope(String source, String docId, com.fasterxml.jackson.databind.JsonNode body) {}
