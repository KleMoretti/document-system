package com.example.docs;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for realtime collaboration operations: role lookup,
 * Yjs state loading, update persistence, and snapshot compaction.
 * Only accesses collab-schema tables.
 */
@Repository
@ConditionalOnRole({ServiceRole.REALTIME, ServiceRole.ALL})
public class RealtimeRepository {
  private final JdbcTemplate jdbc;

  public RealtimeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String getRole(String userId, String docId) {
    return jdbc.queryForObject(
        "SELECT role FROM document_permissions WHERE document_id = ? AND user_id = ?",
        String.class, docId, userId);
  }

  public DocumentState loadDocumentState(String docId) {
    byte[] snapshot = null;
    long snapshotSeq = 0;
    try {
      var row = jdbc.queryForMap(
          "SELECT snapshot_data, last_seq FROM document_snapshots WHERE document_id = ? ORDER BY last_seq DESC LIMIT 1",
          docId);
      snapshot = (byte[]) row.get("snapshot_data");
      snapshotSeq = ((Number) row.get("last_seq")).longValue();
    } catch (EmptyResultDataAccessException ignored) {
      // no snapshot
    }
    var updates = jdbc.query(
        "SELECT update_data FROM document_updates WHERE document_id = ? AND seq > ? ORDER BY seq ASC",
        (rs, rowNum) -> rs.getBytes("update_data"), docId, snapshotSeq);
    return new DocumentState(snapshot, snapshotSeq, updates);
  }

  @Transactional
  public void saveSnapshot(String docId, long lastSeq, byte[] snapshot) {
    if (lastSeq <= 0) {
      throw new BadRequestException("Snapshot sequence must be positive.");
    }
    lockDocument(docId);
    jdbc.update(
        "INSERT INTO document_snapshots (document_id, last_seq, snapshot_data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE snapshot_data = VALUES(snapshot_data), created_at = CURRENT_TIMESTAMP",
        docId, lastSeq, snapshot);
    jdbc.update("DELETE FROM document_updates WHERE document_id = ? AND seq <= ?", docId, lastSeq);
  }

  @Transactional
  public void appendUpdate(String docId, byte[] update) {
    appendUpdates(docId, List.of(update));
  }

  @Transactional
  public void appendUpdates(String docId, List<byte[]> updates) {
    if (updates.isEmpty()) {
      return;
    }
    lockDocument(docId);
    jdbc.update(
        """
        INSERT INTO document_sequences (document_id, next_seq)
        SELECT ?, GREATEST(
          COALESCE((SELECT MAX(seq) FROM document_updates WHERE document_id = ?), 0),
          COALESCE((SELECT MAX(last_seq) FROM document_snapshots WHERE document_id = ?), 0)
        ) + 1
        ON DUPLICATE KEY UPDATE next_seq = next_seq
        """,
        docId, docId, docId);
    var nextSeq = jdbc.queryForObject(
        "SELECT next_seq FROM document_sequences WHERE document_id = ? FOR UPDATE", Long.class, docId);
    jdbc.batchUpdate(
        "INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int index) throws SQLException {
            ps.setString(1, docId);
            ps.setLong(2, nextSeq + index);
            ps.setBytes(3, updates.get(index));
          }

          @Override
          public int getBatchSize() {
            return updates.size();
          }
        });
    jdbc.update("UPDATE document_sequences SET next_seq = ? WHERE document_id = ?", nextSeq + updates.size(), docId);
    jdbc.update(
        "UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ? AND updated_at < CURRENT_TIMESTAMP - INTERVAL 5 SECOND",
        docId);
  }

  public void ping() {
    jdbc.queryForObject("SELECT 1", Integer.class);
  }

  private void lockDocument(String docId) {
    jdbc.queryForObject("SELECT id FROM documents WHERE id = ? FOR UPDATE", String.class, docId);
  }
}
