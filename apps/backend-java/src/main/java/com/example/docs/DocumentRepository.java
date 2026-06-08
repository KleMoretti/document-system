package com.example.docs;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for document CRUD, permissions, comments, and versions.
 * Queries only collab-schema tables (documents, document_permissions,
 * document_updates, document_snapshots, document_versions, document_comments,
 * document_comment_replies). User display info is filled by the caller via
 * {@link UserInfoResolver}.
 */
@Repository
@ConditionalOnRole({ServiceRole.DOCUMENT, ServiceRole.ALL})
public class DocumentRepository {
  private final JdbcTemplate jdbc;

  public DocumentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── documents ────────────────────────────────────────────────────────

  public List<DocumentView> listDocuments(String userId, String query, String status) {
    var normalizedQuery = query == null ? "" : query.trim();
    var normalizedStatus = "deleted".equals(status) ? "deleted" : "active";
    return jdbc.query(
        """
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE p.user_id = ?
          AND ((? = 'deleted' AND d.deleted_at IS NOT NULL) OR (? = 'active' AND d.deleted_at IS NULL))
          AND (? = '' OR LOWER(d.title) LIKE CONCAT('%', LOWER(?), '%'))
        ORDER BY d.updated_at DESC
        """,
        (rs, row) -> document(rs),
        userId, normalizedStatus, normalizedStatus, normalizedQuery, normalizedQuery);
  }

  @Transactional
  public DocumentView createDocument(String ownerId, String title) {
    var id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO documents (id, title, owner_id) VALUES (?, ?, ?)", id, title, ownerId);
    jdbc.update(
        "INSERT INTO document_permissions (document_id, user_id, role) VALUES (?, ?, 'owner')",
        id, ownerId);
    return getDocument(ownerId, id);
  }

  public DocumentView getDocument(String userId, String docId) {
    return jdbc.queryForObject(
        """
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE d.id = ? AND p.user_id = ? AND d.deleted_at IS NULL
        """,
        (rs, row) -> document(rs), docId, userId);
  }

  public DocumentView getDocumentIncludingDeleted(String userId, String docId) {
    return jdbc.queryForObject(
        """
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE d.id = ? AND p.user_id = ?
        """,
        (rs, row) -> document(rs), docId, userId);
  }

  public void renameDocument(String userId, String docId, String title) {
    var updated =
        jdbc.update(
            """
            UPDATE documents d
            JOIN document_permissions p ON p.document_id = d.id
            SET d.title = ?
            WHERE d.id = ? AND p.user_id = ? AND p.role IN ('owner', 'editor') AND d.deleted_at IS NULL
            """,
            title, docId, userId);
    if (updated == 0) {
      throw new ForbiddenException("You cannot rename this document.");
    }
  }

  public void deleteDocument(String docId) {
    jdbc.update("UPDATE documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
  }

  public void restoreDocument(String docId) {
    jdbc.update("UPDATE documents SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
  }

  public void ping() {
    jdbc.queryForObject("SELECT 1", Integer.class);
  }

  // ── permissions / sharing ────────────────────────────────────────────

  /** Returns raw permission rows (userId only, no user display info). */
  public List<ShareView> listShares(String docId) {
    return jdbc.query(
        """
        SELECT p.user_id, p.role
        FROM document_permissions p
        WHERE p.document_id = ?
        ORDER BY FIELD(p.role, 'owner', 'editor', 'viewer'), p.user_id
        """,
        (rs, row) ->
            new ShareView(
                rs.getString("user_id"),
                null, // email filled by caller
                null, // displayName filled by caller
                rs.getString("role")),
        docId);
  }

  public String findUserIdByEmail(String email) {
    // In split mode, this is delegated to AuthInternalClient.
    // For all mode, users table is in the same DB — fallback query.
    try {
      return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, email);
    } catch (EmptyResultDataAccessException ex) {
      throw new UserNotFoundException("User not found.");
    }
  }

  public void shareDocument(String docId, String userId, String role) {
    jdbc.update(
        """
        INSERT INTO document_permissions (document_id, user_id, role)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE role = VALUES(role)
        """,
        docId, userId, role);
  }

  public void removeShare(String docId, String userId) {
    jdbc.update(
        "DELETE FROM document_permissions WHERE document_id = ? AND user_id = ? AND role <> 'owner'",
        docId, userId);
  }

  // ── versions ─────────────────────────────────────────────────────────

  public List<DocumentVersionSummary> listVersions(String docId) {
    return jdbc.query(
        """
        SELECT id, document_id, label, created_by, created_at
        FROM document_versions
        WHERE document_id = ?
        ORDER BY created_at DESC
        """,
        (rs, row) -> versionSummary(rs), docId);
  }

  public DocumentVersionSummary createVersion(String docId, String userId, String label) {
    var id = UUID.randomUUID().toString();
    var rawUpdates = new ArrayList<byte[]>();
    long snapshotSeq = 0;
    try {
      var row = jdbc.queryForMap(
          "SELECT snapshot_data, last_seq FROM document_snapshots WHERE document_id = ? ORDER BY last_seq DESC LIMIT 1",
          docId);
      var snapshot = (byte[]) row.get("snapshot_data");
      snapshotSeq = ((Number) row.get("last_seq")).longValue();
      if (snapshot != null && snapshot.length > 0) {
        rawUpdates.add(snapshot);
      }
    } catch (EmptyResultDataAccessException ignored) {
      // no snapshot
    }
    var updates = jdbc.query(
        "SELECT update_data FROM document_updates WHERE document_id = ? AND seq > ? ORDER BY seq ASC",
        (rs, row2) -> rs.getBytes("update_data"), docId, snapshotSeq);
    rawUpdates.addAll(updates);
    var encoded = rawUpdates.stream().map(u -> Base64.getEncoder().encodeToString(u)).toList();
    var versionState = String.join("\n", encoded).getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "INSERT INTO document_versions (id, document_id, label, state_data, created_by) VALUES (?, ?, ?, ?, ?)",
        id, docId, label == null || label.isBlank() ? "Manual version" : label.trim(),
        versionState, userId);
    return getVersionSummary(docId, id);
  }

  public DocumentVersion getVersion(String docId, String versionId) {
    return jdbc.queryForObject(
        """
        SELECT id, document_id, label, created_by, created_at, state_data
        FROM document_versions
        WHERE document_id = ? AND id = ?
        """,
        (rs, row) ->
            new DocumentVersion(
                rs.getString("id"),
                rs.getString("document_id"),
                rs.getString("label"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                splitUpdates(rs.getBytes("state_data"))),
        docId, versionId);
  }

  @Transactional
  public void restoreVersion(String docId, String versionId) {
    var version = getVersion(docId, versionId);
    lockDocument(docId);
    jdbc.update("DELETE FROM document_updates WHERE document_id = ?", docId);
    jdbc.update("DELETE FROM document_snapshots WHERE document_id = ?", docId);
    long seq = 1;
    for (String update : version.updates()) {
      jdbc.update(
          "INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)",
          docId, seq, Base64.getDecoder().decode(update));
      seq += 1;
    }
    jdbc.update(
        "INSERT INTO document_sequences (document_id, next_seq) VALUES (?, ?) ON DUPLICATE KEY UPDATE next_seq = VALUES(next_seq)",
        docId, seq);
    jdbc.update("UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
  }

  // ── comments ─────────────────────────────────────────────────────────

  public List<CommentThread> listComments(String docId) {
    var comments = jdbc.query(
        """
        SELECT c.id, c.document_id, c.author_id, c.body, c.resolved, c.created_at, c.updated_at
        FROM document_comments c
        WHERE c.document_id = ?
        ORDER BY c.created_at DESC
        """,
        (rs, row) -> comment(rs, List.of()), docId);
    var allIds = comments.stream().map(CommentThread::id).toList();
    var repliesByComment = listRepliesForComments(allIds);
    return comments.stream()
        .map(c -> new CommentThread(
            c.id(), c.documentId(), c.authorId(), null, c.body(),
            c.resolved(), c.createdAt(), c.updatedAt(),
            repliesByComment.getOrDefault(c.id(), List.of())))
        .toList();
  }

  public CommentThread createComment(String docId, String userId, String body) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO document_comments (id, document_id, author_id, body) VALUES (?, ?, ?, ?)",
        id, docId, userId, body);
    return getComment(docId, id);
  }

  public CommentThread addReply(String docId, String commentId, String userId, String body) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO document_comment_replies (id, comment_id, author_id, body) VALUES (?, ?, ?, ?)",
        id, commentId, userId, body);
    return getComment(docId, commentId);
  }

  public CommentThread updateComment(String docId, String commentId, UpdateCommentRequest req) {
    var body = req.body();
    var resolved = req.resolved();
    if (body != null) {
      jdbc.update("UPDATE document_comments SET body = ? WHERE document_id = ? AND id = ?", body, docId, commentId);
    }
    if (resolved != null) {
      jdbc.update("UPDATE document_comments SET resolved = ? WHERE document_id = ? AND id = ?", resolved, docId, commentId);
    }
    return getComment(docId, commentId);
  }

  // ── helpers ──────────────────────────────────────────────────────────

  private void lockDocument(String docId) {
    jdbc.queryForObject("SELECT id FROM documents WHERE id = ? FOR UPDATE", String.class, docId);
  }

  private DocumentVersionSummary getVersionSummary(String docId, String versionId) {
    return jdbc.queryForObject(
        "SELECT id, document_id, label, created_by, created_at FROM document_versions WHERE document_id = ? AND id = ?",
        (rs, row) -> versionSummary(rs), docId, versionId);
  }

  private List<String> splitUpdates(byte[] data) {
    var text = new String(data, StandardCharsets.UTF_8);
    if (text.isBlank()) {
      return List.of();
    }
    return List.of(text.split("\n"));
  }

  private DocumentVersionSummary versionSummary(ResultSet rs) throws SQLException {
    return new DocumentVersionSummary(
        rs.getString("id"),
        rs.getString("document_id"),
        rs.getString("label"),
        rs.getString("created_by"),
        rs.getTimestamp("created_at").toInstant());
  }

  private CommentThread getComment(String docId, String commentId) {
    return jdbc.queryForObject(
        """
        SELECT c.id, c.document_id, c.author_id, c.body, c.resolved, c.created_at, c.updated_at
        FROM document_comments c
        WHERE c.document_id = ? AND c.id = ?
        """,
        (rs, row) -> comment(rs, listReplies(commentId)), docId, commentId);
  }

  private List<CommentReply> listReplies(String commentId) {
    return jdbc.query(
        """
        SELECT r.id, r.comment_id, r.author_id, r.body, r.created_at
        FROM document_comment_replies r
        WHERE r.comment_id = ?
        ORDER BY r.created_at ASC
        """,
        (rs, row) ->
            new CommentReply(
                rs.getString("id"),
                rs.getString("comment_id"),
                rs.getString("author_id"),
                null, // authorName filled by caller
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()),
        commentId);
  }

  private java.util.Map<String, List<CommentReply>> listRepliesForComments(List<String> commentIds) {
    var result = new java.util.HashMap<String, List<CommentReply>>();
    if (commentIds.isEmpty()) {
      return result;
    }
    var placeholders = String.join(",", java.util.Collections.nCopies(commentIds.size(), "?"));
    jdbc.query(
        "SELECT r.id, r.comment_id, r.author_id, r.body, r.created_at FROM document_comment_replies r WHERE r.comment_id IN ("
            + placeholders
            + ") ORDER BY r.comment_id ASC, r.created_at ASC",
        rs -> {
          var reply = new CommentReply(
              rs.getString("id"),
              rs.getString("comment_id"),
              rs.getString("author_id"),
              null,
              rs.getString("body"),
              rs.getTimestamp("created_at").toInstant());
          result.computeIfAbsent(reply.commentId(), ignored -> new ArrayList<>()).add(reply);
        },
        commentIds.toArray());
    return result;
  }

  private CommentThread comment(ResultSet rs, List<CommentReply> replies) throws SQLException {
    return new CommentThread(
        rs.getString("id"),
        rs.getString("document_id"),
        rs.getString("author_id"),
        null, // authorName filled by caller
        rs.getString("body"),
        rs.getBoolean("resolved"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        replies);
  }

  private DocumentView document(ResultSet rs) throws SQLException {
    var deletedAt = rs.getTimestamp("deleted_at");
    return new DocumentView(
        rs.getString("id"),
        rs.getString("title"),
        rs.getString("owner_id"),
        rs.getString("role"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        deletedAt == null ? null : deletedAt.toInstant());
  }
}
