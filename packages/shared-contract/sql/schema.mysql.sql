CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS documents (
  id VARCHAR(36) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  owner_id VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_permissions (
  document_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  role ENUM('owner', 'editor', 'viewer') NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id, user_id),
  CONSTRAINT fk_permissions_document FOREIGN KEY (document_id) REFERENCES documents(id),
  CONSTRAINT fk_permissions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_sequences (
  document_id VARCHAR(36) PRIMARY KEY,
  next_seq BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sequences_document FOREIGN KEY (document_id) REFERENCES documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_updates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL,
  seq BIGINT NOT NULL,
  update_data MEDIUMBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_document_updates_seq (document_id, seq),
  KEY idx_document_updates_document (document_id, id),
  CONSTRAINT fk_updates_document FOREIGN KEY (document_id) REFERENCES documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_snapshots (
  document_id VARCHAR(36) NOT NULL,
  last_seq BIGINT NOT NULL,
  snapshot_data MEDIUMBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id, last_seq),
  KEY idx_document_snapshots_latest (document_id, last_seq),
  CONSTRAINT fk_snapshots_document FOREIGN KEY (document_id) REFERENCES documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_versions (
  id VARCHAR(36) PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL,
  label VARCHAR(255) NOT NULL,
  state_data MEDIUMBLOB NOT NULL,
  created_by VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_document_versions_document (document_id, created_at),
  CONSTRAINT fk_versions_document FOREIGN KEY (document_id) REFERENCES documents(id),
  CONSTRAINT fk_versions_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_comments (
  id VARCHAR(36) PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL,
  author_id VARCHAR(36) NOT NULL,
  body TEXT NOT NULL,
  resolved BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_comments_document (document_id, created_at),
  CONSTRAINT fk_comments_document FOREIGN KEY (document_id) REFERENCES documents(id),
  CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_comment_replies (
  id VARCHAR(36) PRIMARY KEY,
  comment_id VARCHAR(36) NOT NULL,
  author_id VARCHAR(36) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_comment_replies_comment (comment_id, created_at),
  CONSTRAINT fk_comment_replies_comment FOREIGN KEY (comment_id) REFERENCES document_comments(id),
  CONSTRAINT fk_comment_replies_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
