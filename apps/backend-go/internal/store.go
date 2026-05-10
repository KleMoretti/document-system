package internal

import (
	"context"
	"database/sql"
	"errors"

	"golang.org/x/crypto/bcrypt"
)

type Store struct {
	db *sql.DB
}

func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) CreateUser(ctx context.Context, req RegisterRequest) (User, string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		return User{}, "", err
	}
	user := User{ID: NewID(), Email: req.Email, DisplayName: req.DisplayName}
	_, err = s.db.ExecContext(ctx, "INSERT INTO users (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)", user.ID, user.Email, string(hash), user.DisplayName)
	return user, string(hash), err
}

func (s *Store) FindUserForLogin(ctx context.Context, email string) (User, string, error) {
	row := s.db.QueryRowContext(ctx, "SELECT id, email, display_name, created_at, password_hash FROM users WHERE email = ?", email)
	var user User
	var hash string
	err := row.Scan(&user.ID, &user.Email, &user.DisplayName, &user.CreatedAt, &hash)
	return user, hash, err
}

func (s *Store) FindUser(ctx context.Context, id string) (User, error) {
	row := s.db.QueryRowContext(ctx, "SELECT id, email, display_name, created_at FROM users WHERE id = ?", id)
	var user User
	err := row.Scan(&user.ID, &user.Email, &user.DisplayName, &user.CreatedAt)
	return user, err
}

func (s *Store) ListDocuments(ctx context.Context, userID string) ([]Document, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at
		FROM documents d
		JOIN document_permissions p ON p.document_id = d.id
		WHERE p.user_id = ? AND d.deleted_at IS NULL
		ORDER BY d.updated_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var docs []Document
	for rows.Next() {
		var doc Document
		if err := rows.Scan(&doc.ID, &doc.Title, &doc.OwnerID, &doc.Role, &doc.CreatedAt, &doc.UpdatedAt); err != nil {
			return nil, err
		}
		docs = append(docs, doc)
	}
	return docs, rows.Err()
}

func (s *Store) CreateDocument(ctx context.Context, ownerID, title string) (Document, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Document{}, err
	}
	defer tx.Rollback()

	doc := Document{ID: NewID(), Title: title, OwnerID: ownerID, Role: "owner"}
	if _, err := tx.ExecContext(ctx, "INSERT INTO documents (id, title, owner_id) VALUES (?, ?, ?)", doc.ID, doc.Title, doc.OwnerID); err != nil {
		return Document{}, err
	}
	if _, err := tx.ExecContext(ctx, "INSERT INTO document_permissions (document_id, user_id, role) VALUES (?, ?, 'owner')", doc.ID, ownerID); err != nil {
		return Document{}, err
	}
	return doc, tx.Commit()
}

func (s *Store) GetDocument(ctx context.Context, userID, docID string) (Document, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at
		FROM documents d
		JOIN document_permissions p ON p.document_id = d.id
		WHERE d.id = ? AND p.user_id = ? AND d.deleted_at IS NULL`, docID, userID)
	var doc Document
	err := row.Scan(&doc.ID, &doc.Title, &doc.OwnerID, &doc.Role, &doc.CreatedAt, &doc.UpdatedAt)
	return doc, err
}

func (s *Store) RenameDocument(ctx context.Context, docID, title string) error {
	_, err := s.db.ExecContext(ctx, "UPDATE documents SET title = ? WHERE id = ? AND deleted_at IS NULL", title, docID)
	return err
}

func (s *Store) DeleteDocument(ctx context.Context, docID string) error {
	_, err := s.db.ExecContext(ctx, "UPDATE documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", docID)
	return err
}

func (s *Store) GetRole(ctx context.Context, userID, docID string) (string, error) {
	row := s.db.QueryRowContext(ctx, "SELECT role FROM document_permissions WHERE document_id = ? AND user_id = ?", docID, userID)
	var role string
	err := row.Scan(&role)
	return role, err
}

func (s *Store) ShareDocument(ctx context.Context, docID string, req ShareDocumentRequest) error {
	if !ValidRole(req.Role) || req.Role == "owner" {
		return errors.New("invalid role")
	}
	var userID string
	if err := s.db.QueryRowContext(ctx, "SELECT id FROM users WHERE email = ?", req.Email).Scan(&userID); err != nil {
		return err
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO document_permissions (document_id, user_id, role)
		VALUES (?, ?, ?)
		ON DUPLICATE KEY UPDATE role = VALUES(role)`, docID, userID, req.Role)
	return err
}

func (s *Store) ListShares(ctx context.Context, docID string) ([]Share, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT u.id, u.email, u.display_name, p.role
		FROM document_permissions p
		JOIN users u ON u.id = p.user_id
		WHERE p.document_id = ?
		ORDER BY FIELD(p.role, 'owner', 'editor', 'viewer'), u.email`, docID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var shares []Share
	for rows.Next() {
		var share Share
		if err := rows.Scan(&share.UserID, &share.Email, &share.DisplayName, &share.Role); err != nil {
			return nil, err
		}
		shares = append(shares, share)
	}
	return shares, rows.Err()
}

func (s *Store) RemoveShare(ctx context.Context, docID, userID string) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM document_permissions WHERE document_id = ? AND user_id = ? AND role <> 'owner'", docID, userID)
	return err
}

func (s *Store) AppendUpdate(ctx context.Context, docID string, update []byte) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var nextSeq int64
	row := tx.QueryRowContext(ctx, "SELECT COALESCE(MAX(seq), 0) + 1 FROM document_updates WHERE document_id = ? FOR UPDATE", docID)
	if err := row.Scan(&nextSeq); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)", docID, nextSeq, update); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, "UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", docID)
	if err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) LoadUpdates(ctx context.Context, docID string) ([][]byte, error) {
	rows, err := s.db.QueryContext(ctx, "SELECT update_data FROM document_updates WHERE document_id = ? ORDER BY seq ASC", docID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var updates [][]byte
	for rows.Next() {
		var update []byte
		if err := rows.Scan(&update); err != nil {
			return nil, err
		}
		updates = append(updates, update)
	}
	return updates, rows.Err()
}
