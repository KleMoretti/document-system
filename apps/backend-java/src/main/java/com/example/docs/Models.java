package com.example.docs;

import java.time.Instant;

record User(String id, String email, String displayName, Instant createdAt) {}

record UserClaims(String userId, String email) {}

record DocumentView(
    String id,
    String title,
    String ownerId,
    String role,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}

record ShareView(String userId, String email, String displayName, String role) {}

record RegisterRequest(String email, String password, String displayName) {}

record LoginRequest(String email, String password) {}

record AuthResponse(String token, User user) {}

record CreateDocumentRequest(String title) {}

record RenameDocumentRequest(String title) {}

record ShareDocumentRequest(String email, String role) {}

record CreateVersionRequest(String label) {}

record DocumentVersionSummary(
    String id, String documentId, String label, String createdBy, Instant createdAt) {}

record DocumentVersion(
    String id, String documentId, String label, String createdBy, Instant createdAt, java.util.List<String> updates) {}

record CreateCommentRequest(String body) {}

record CreateReplyRequest(String body) {}

record UpdateCommentRequest(String body, Boolean resolved) {}

record CommentReply(
    String id, String commentId, String authorId, String authorName, String body, Instant createdAt) {}

record CommentThread(
    String id,
    String documentId,
    String authorId,
    String authorName,
    String body,
    boolean resolved,
    Instant createdAt,
    Instant updatedAt,
    java.util.List<CommentReply> replies) {}

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
