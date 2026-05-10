package com.example.docs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    return jdbc.query(
        """
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE p.user_id = ? AND d.deleted_at IS NULL
        ORDER BY d.updated_at DESC
        """,
        (rs, row) -> document(rs),
        userId);
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
        SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at
        FROM documents d
        JOIN document_permissions p ON p.document_id = d.id
        WHERE d.id = ? AND p.user_id = ? AND d.deleted_at IS NULL
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

  public void renameDocument(String docId, String title) {
    jdbc.update("UPDATE documents SET title = ? WHERE id = ? AND deleted_at IS NULL", title, docId);
  }

  public void deleteDocument(String docId) {
    jdbc.update("UPDATE documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", docId);
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
    var userId =
        jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, req.email());
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

  private DocumentView document(ResultSet rs) throws SQLException {
    return new DocumentView(
        rs.getString("id"),
        rs.getString("title"),
        rs.getString("owner_id"),
        rs.getString("role"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  public record LoginUser(
      String id, String email, String displayName, Instant createdAt, String passwordHash) {}
}
