package com.example.docs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Repository
public class AppRepository {
  private final JdbcTemplate jdbc;

  public AppRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public User createUser(String email, String passwordHash, String displayName) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
        id,
        email,
        passwordHash,
        displayName);
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

  public List<DocumentView> listDocuments(String userId) {
    return listDocuments(userId, "", "active");
  }

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
        userId,
        normalizedStatus,
        normalizedStatus,
        normalizedQuery,
        normalizedQuery);
  }

  @Transactional
  public DocumentView createDocument(String ownerId, String title) {
    var id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO documents (id, title, owner_id) VALUES (?, ?, ?)", id, title, ownerId);
    jdbc.update(
        "INSERT INTO document_permissions (document_id, user_id, role) VALUES (?, ?, 'owner')",
        id,
        ownerId);
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
        (rs, row) -> document(rs),
        docId,
        userId);
  }

  public DocumentView getDocumentIncludingDeleted(String userId, String docId) {
    return jdbc.queryForObject(
        """
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE d.id = ? AND p.user_id = ?
        """,
        (rs, row) -> document(rs),
        docId,
        userId);
  }

  public String getRole(String userId, String docId) {
    return jdbc.queryForObject(
        "SELECT role FROM document_permissions WHERE document_id = ? AND user_id = ?",
        String.class,
        docId,
        userId);
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
            title,
            docId,
            userId);
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

  public List<ShareView> listShares(String docId) {
    return jdbc.query(
        """
        SELECT u.id, u.email, u.display_name, p.role
        FROM document_permissions p
        JOIN users u ON u.id = p.user_id
        WHERE p.document_id = ?
        ORDER BY FIELD(p.role, 'owner', 'editor', 'viewer'), u.email
        """,
        (rs, row) ->
            new ShareView(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("role")),
        docId);
  }

  public void shareDocument(String docId, ShareDocumentRequest req) {
    String userId;
    try {
      userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, req.email());
    } catch (EmptyResultDataAccessException ex) {
      throw new UserNotFoundException("User not found.");
    }
    jdbc.update(
        """
        INSERT INTO document_permissions (document_id, user_id, role)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE role = VALUES(role)
        """,
        docId,
        userId,
        req.role());
  }

  public void removeShare(String docId, String userId) {
    jdbc.update(
        "DELETE FROM document_permissions WHERE document_id = ? AND user_id = ? AND role <> 'owner'",
        docId,
        userId);
  }

  @Transactional
  public void appendUpdate(String docId, byte[] update) {
    var seq =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(seq), 0) + 1 FROM document_updates WHERE document_id = ? FOR UPDATE",
            Long.class,
            docId);
    jdbc.update(
        "INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)",
        docId,
        seq,
        update);
    jdbc.update("UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
  }

  public List<byte[]> loadUpdates(String docId) {
    return jdbc.query(
        "SELECT update_data FROM document_updates WHERE document_id = ? ORDER BY seq ASC",
        (rs, row) -> rs.getBytes("update_data"),
        docId);
  }

  public List<DocumentVersionSummary> listVersions(String docId) {
    return jdbc.query(
        """
        SELECT id, document_id, label, created_by, created_at
        FROM document_versions
        WHERE document_id = ?
        ORDER BY created_at DESC
        """,
        (rs, row) -> versionSummary(rs),
        docId);
  }

  public DocumentVersionSummary createVersion(String docId, String userId, String label) {
    var id = UUID.randomUUID().toString();
    var updates =
        loadUpdates(docId).stream().map(update -> Base64.getEncoder().encodeToString(update)).toList();
    var state = String.join("\n", updates).getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "INSERT INTO document_versions (id, document_id, label, state_data, created_by) VALUES (?, ?, ?, ?, ?)",
        id,
        docId,
        label == null || label.isBlank() ? "Manual version" : label.trim(),
        state,
        userId);
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
        docId,
        versionId);
  }

  @Transactional
  public void restoreVersion(String docId, String versionId) {
    var version = getVersion(docId, versionId);
    jdbc.update("DELETE FROM document_updates WHERE document_id = ?", docId);
    long seq = 1;
    for (String update : version.updates()) {
      jdbc.update(
          "INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)",
          docId,
          seq,
          Base64.getDecoder().decode(update));
      seq += 1;
    }
    jdbc.update("UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
  }

  public List<CommentThread> listComments(String docId) {
    return jdbc.query(
        """
        SELECT c.id, c.document_id, c.author_id, u.display_name, c.body, c.resolved, c.created_at, c.updated_at
        FROM document_comments c
        JOIN users u ON u.id = c.author_id
        WHERE c.document_id = ?
        ORDER BY c.created_at DESC
        """,
        (rs, row) -> comment(rs, listReplies(rs.getString("id"))),
        docId);
  }

  public CommentThread createComment(String docId, String userId, String body) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO document_comments (id, document_id, author_id, body) VALUES (?, ?, ?, ?)",
        id,
        docId,
        userId,
        body);
    return getComment(docId, id);
  }

  public CommentThread addReply(String docId, String commentId, String userId, String body) {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO document_comment_replies (id, comment_id, author_id, body) VALUES (?, ?, ?, ?)",
        id,
        commentId,
        userId,
        body);
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

  private DocumentVersionSummary getVersionSummary(String docId, String versionId) {
    return jdbc.queryForObject(
        """
        SELECT id, document_id, label, created_by, created_at
        FROM document_versions
        WHERE document_id = ? AND id = ?
        """,
        (rs, row) -> versionSummary(rs),
        docId,
        versionId);
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
        SELECT c.id, c.document_id, c.author_id, u.display_name, c.body, c.resolved, c.created_at, c.updated_at
        FROM document_comments c
        JOIN users u ON u.id = c.author_id
        WHERE c.document_id = ? AND c.id = ?
        """,
        (rs, row) -> comment(rs, listReplies(commentId)),
        docId,
        commentId);
  }

  private List<CommentReply> listReplies(String commentId) {
    return jdbc.query(
        """
        SELECT r.id, r.comment_id, r.author_id, u.display_name, r.body, r.created_at
        FROM document_comment_replies r
        JOIN users u ON u.id = r.author_id
        WHERE r.comment_id = ?
        ORDER BY r.created_at ASC
        """,
        (rs, row) ->
            new CommentReply(
                rs.getString("id"),
                rs.getString("comment_id"),
                rs.getString("author_id"),
                rs.getString("display_name"),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()),
        commentId);
  }

  private CommentThread comment(ResultSet rs, List<CommentReply> replies) throws SQLException {
    return new CommentThread(
        rs.getString("id"),
        rs.getString("document_id"),
        rs.getString("author_id"),
        rs.getString("display_name"),
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

  public record LoginUser(
      String id, String email, String displayName, Instant createdAt, String passwordHash) {}
}
