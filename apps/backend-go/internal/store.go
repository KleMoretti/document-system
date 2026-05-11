package internal

import (
	"context"
	"database/sql"
	"encoding/base64"
	"errors"
	"strings"

	"golang.org/x/crypto/bcrypt"
)

type Store struct {
	db *sql.DB
}

var (
	ErrForbidden    = errors.New("forbidden")
	ErrUserNotFound = errors.New("user not found")
)

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
	return s.ListDocumentsFiltered(ctx, userID, "", "active")
}

func (s *Store) ListDocumentsFiltered(ctx context.Context, userID, query, status string) ([]Document, error) {
	normalizedStatus := "active"
	if status == "deleted" {
		normalizedStatus = "deleted"
	}
	normalizedQuery := strings.TrimSpace(query)
	rows, err := s.db.QueryContext(ctx, `
		SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
		FROM documents d
		JOIN document_permissions p ON p.document_id = d.id
		WHERE p.user_id = ?
		  AND ((? = 'deleted' AND d.deleted_at IS NOT NULL) OR (? = 'active' AND d.deleted_at IS NULL))
		  AND (? = '' OR LOWER(d.title) LIKE CONCAT('%', LOWER(?), '%'))
		ORDER BY d.updated_at DESC`, userID, normalizedStatus, normalizedStatus, normalizedQuery, normalizedQuery)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var docs []Document
	for rows.Next() {
		var doc Document
		if err := scanDocument(rows, &doc); err != nil {
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
		SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
		FROM documents d
		JOIN document_permissions p ON p.document_id = d.id
		WHERE d.id = ? AND p.user_id = ? AND d.deleted_at IS NULL`, docID, userID)
	var doc Document
	err := scanDocument(row, &doc)
	return doc, err
}

func (s *Store) GetDocumentIncludingDeleted(ctx context.Context, userID, docID string) (Document, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT d.id, d.title, d.owner_id, p.role, d.created_at, d.updated_at, d.deleted_at
		FROM documents d
		JOIN document_permissions p ON p.document_id = d.id
		WHERE d.id = ? AND p.user_id = ?`, docID, userID)
	var doc Document
	err := scanDocument(row, &doc)
	return doc, err
}

func (s *Store) RenameDocument(ctx context.Context, userID, docID, title string) error {
	result, err := s.db.ExecContext(ctx, `
		UPDATE documents d
		JOIN document_permissions p ON p.document_id = d.id
		SET d.title = ?
		WHERE d.id = ? AND p.user_id = ? AND p.role IN ('owner', 'editor') AND d.deleted_at IS NULL`, title, docID, userID)
	if err != nil {
		return err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return ErrForbidden
	}
	return nil
}

func (s *Store) DeleteDocument(ctx context.Context, docID string) error {
	_, err := s.db.ExecContext(ctx, "UPDATE documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", docID)
	return err
}

func (s *Store) RestoreDocument(ctx context.Context, docID string) error {
	_, err := s.db.ExecContext(ctx, "UPDATE documents SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?", docID)
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
		if errors.Is(err, sql.ErrNoRows) {
			return ErrUserNotFound
		}
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

func (s *Store) ListVersions(ctx context.Context, docID string) ([]DocumentVersionSummary, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, document_id, label, created_by, created_at
		FROM document_versions
		WHERE document_id = ?
		ORDER BY created_at DESC`, docID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var versions []DocumentVersionSummary
	for rows.Next() {
		var version DocumentVersionSummary
		if err := rows.Scan(&version.ID, &version.DocumentID, &version.Label, &version.CreatedBy, &version.CreatedAt); err != nil {
			return nil, err
		}
		versions = append(versions, version)
	}
	return versions, rows.Err()
}

func (s *Store) CreateVersion(ctx context.Context, docID, userID, label string) (DocumentVersionSummary, error) {
	if strings.TrimSpace(label) == "" {
		label = "Manual version"
	}
	updates, err := s.LoadUpdates(ctx, docID)
	if err != nil {
		return DocumentVersionSummary{}, err
	}
	encoded := make([]string, 0, len(updates))
	for _, update := range updates {
		encoded = append(encoded, base64.StdEncoding.EncodeToString(update))
	}
	id := NewID()
	_, err = s.db.ExecContext(ctx,
		"INSERT INTO document_versions (id, document_id, label, state_data, created_by) VALUES (?, ?, ?, ?, ?)",
		id, docID, strings.TrimSpace(label), []byte(strings.Join(encoded, "\n")), userID)
	if err != nil {
		return DocumentVersionSummary{}, err
	}
	return s.GetVersionSummary(ctx, docID, id)
}

func (s *Store) GetVersionSummary(ctx context.Context, docID, versionID string) (DocumentVersionSummary, error) {
	var version DocumentVersionSummary
	err := s.db.QueryRowContext(ctx, `
		SELECT id, document_id, label, created_by, created_at
		FROM document_versions
		WHERE document_id = ? AND id = ?`, docID, versionID).
		Scan(&version.ID, &version.DocumentID, &version.Label, &version.CreatedBy, &version.CreatedAt)
	return version, err
}

func (s *Store) GetVersion(ctx context.Context, docID, versionID string) (DocumentVersion, error) {
	var version DocumentVersion
	var state []byte
	err := s.db.QueryRowContext(ctx, `
		SELECT id, document_id, label, created_by, created_at, state_data
		FROM document_versions
		WHERE document_id = ? AND id = ?`, docID, versionID).
		Scan(&version.ID, &version.DocumentID, &version.Label, &version.CreatedBy, &version.CreatedAt, &state)
	if err != nil {
		return version, err
	}
	text := strings.TrimSpace(string(state))
	if text == "" {
		version.Updates = []string{}
	} else {
		version.Updates = strings.Split(text, "\n")
	}
	return version, nil
}

func (s *Store) RestoreVersion(ctx context.Context, docID, versionID string) error {
	version, err := s.GetVersion(ctx, docID, versionID)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, "DELETE FROM document_updates WHERE document_id = ?", docID); err != nil {
		return err
	}
	for index, update := range version.Updates {
		decoded, err := base64.StdEncoding.DecodeString(update)
		if err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx,
			"INSERT INTO document_updates (document_id, seq, update_data) VALUES (?, ?, ?)",
			docID, index+1, decoded); err != nil {
			return err
		}
	}
	if _, err := tx.ExecContext(ctx, "UPDATE documents SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", docID); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) ListComments(ctx context.Context, docID string) ([]CommentThread, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT c.id, c.document_id, c.author_id, u.display_name, c.body, c.resolved, c.created_at, c.updated_at
		FROM document_comments c
		JOIN users u ON u.id = c.author_id
		WHERE c.document_id = ?
		ORDER BY c.created_at DESC`, docID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var comments []CommentThread
	for rows.Next() {
		var comment CommentThread
		if err := rows.Scan(&comment.ID, &comment.DocumentID, &comment.AuthorID, &comment.AuthorName, &comment.Body, &comment.Resolved, &comment.CreatedAt, &comment.UpdatedAt); err != nil {
			return nil, err
		}
		replies, err := s.ListReplies(ctx, comment.ID)
		if err != nil {
			return nil, err
		}
		comment.Replies = replies
		comments = append(comments, comment)
	}
	return comments, rows.Err()
}

func (s *Store) CreateComment(ctx context.Context, docID, userID, body string) (CommentThread, error) {
	id := NewID()
	_, err := s.db.ExecContext(ctx,
		"INSERT INTO document_comments (id, document_id, author_id, body) VALUES (?, ?, ?, ?)",
		id, docID, userID, body)
	if err != nil {
		return CommentThread{}, err
	}
	return s.GetComment(ctx, docID, id)
}

func (s *Store) AddReply(ctx context.Context, docID, commentID, userID, body string) (CommentThread, error) {
	id := NewID()
	_, err := s.db.ExecContext(ctx,
		"INSERT INTO document_comment_replies (id, comment_id, author_id, body) VALUES (?, ?, ?, ?)",
		id, commentID, userID, body)
	if err != nil {
		return CommentThread{}, err
	}
	return s.GetComment(ctx, docID, commentID)
}

func (s *Store) UpdateComment(ctx context.Context, docID, commentID string, req UpdateCommentRequest) (CommentThread, error) {
	if req.Body != nil {
		if _, err := s.db.ExecContext(ctx, "UPDATE document_comments SET body = ? WHERE document_id = ? AND id = ?", *req.Body, docID, commentID); err != nil {
			return CommentThread{}, err
		}
	}
	if req.Resolved != nil {
		if _, err := s.db.ExecContext(ctx, "UPDATE document_comments SET resolved = ? WHERE document_id = ? AND id = ?", *req.Resolved, docID, commentID); err != nil {
			return CommentThread{}, err
		}
	}
	return s.GetComment(ctx, docID, commentID)
}

func (s *Store) GetComment(ctx context.Context, docID, commentID string) (CommentThread, error) {
	var comment CommentThread
	err := s.db.QueryRowContext(ctx, `
		SELECT c.id, c.document_id, c.author_id, u.display_name, c.body, c.resolved, c.created_at, c.updated_at
		FROM document_comments c
		JOIN users u ON u.id = c.author_id
		WHERE c.document_id = ? AND c.id = ?`, docID, commentID).
		Scan(&comment.ID, &comment.DocumentID, &comment.AuthorID, &comment.AuthorName, &comment.Body, &comment.Resolved, &comment.CreatedAt, &comment.UpdatedAt)
	if err != nil {
		return comment, err
	}
	comment.Replies, err = s.ListReplies(ctx, comment.ID)
	return comment, err
}

func (s *Store) ListReplies(ctx context.Context, commentID string) ([]CommentReply, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT r.id, r.comment_id, r.author_id, u.display_name, r.body, r.created_at
		FROM document_comment_replies r
		JOIN users u ON u.id = r.author_id
		WHERE r.comment_id = ?
		ORDER BY r.created_at ASC`, commentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var replies []CommentReply
	for rows.Next() {
		var reply CommentReply
		if err := rows.Scan(&reply.ID, &reply.CommentID, &reply.AuthorID, &reply.AuthorName, &reply.Body, &reply.CreatedAt); err != nil {
			return nil, err
		}
		replies = append(replies, reply)
	}
	return replies, rows.Err()
}

type documentScanner interface {
	Scan(dest ...any) error
}

func scanDocument(row documentScanner, doc *Document) error {
	var deletedAt sql.NullTime
	if err := row.Scan(&doc.ID, &doc.Title, &doc.OwnerID, &doc.Role, &doc.CreatedAt, &doc.UpdatedAt, &deletedAt); err != nil {
		return err
	}
	if deletedAt.Valid {
		doc.DeletedAt = &deletedAt.Time
	}
	return nil
}
