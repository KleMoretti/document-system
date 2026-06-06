package internal

import "time"

type User struct {
	ID          string    `json:"id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"displayName"`
	CreatedAt   time.Time `json:"createdAt"`
}

type Document struct {
	ID        string     `json:"id"`
	Title     string     `json:"title"`
	OwnerID   string     `json:"ownerId"`
	Role      string     `json:"role"`
	CreatedAt time.Time  `json:"createdAt"`
	UpdatedAt time.Time  `json:"updatedAt"`
	DeletedAt *time.Time `json:"deletedAt,omitempty"`
}

type Share struct {
	UserID      string `json:"userId"`
	Email       string `json:"email"`
	DisplayName string `json:"displayName"`
	Role        string `json:"role"`
}

type RegisterRequest struct {
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"displayName"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type AuthResponse struct {
	Token string `json:"token"`
	User  User   `json:"user"`
}

type CreateDocumentRequest struct {
	Title string `json:"title"`
}

type RenameDocumentRequest struct {
	Title string `json:"title"`
}

type ShareDocumentRequest struct {
	Email string `json:"email"`
	Role  string `json:"role"`
}

type CreateVersionRequest struct {
	Label string `json:"label"`
}

type DocumentVersionSummary struct {
	ID         string    `json:"id"`
	DocumentID string    `json:"documentId"`
	Label      string    `json:"label"`
	CreatedBy  string    `json:"createdBy"`
	CreatedAt  time.Time `json:"createdAt"`
}

type DocumentVersion struct {
	DocumentVersionSummary
	Updates []string `json:"updates"`
}

type CreateCommentRequest struct {
	Body string `json:"body"`
}

type CreateReplyRequest struct {
	Body string `json:"body"`
}

type UpdateCommentRequest struct {
	Body     *string `json:"body,omitempty"`
	Resolved *bool   `json:"resolved,omitempty"`
}

type CommentReply struct {
	ID         string    `json:"id"`
	CommentID  string    `json:"commentId"`
	AuthorID   string    `json:"authorId"`
	AuthorName string    `json:"authorName"`
	Body       string    `json:"body"`
	CreatedAt  time.Time `json:"createdAt"`
}

type CommentThread struct {
	ID         string         `json:"id"`
	DocumentID string         `json:"documentId"`
	AuthorID   string         `json:"authorId"`
	AuthorName string         `json:"authorName"`
	Body       string         `json:"body"`
	Resolved   bool           `json:"resolved"`
	CreatedAt  time.Time      `json:"createdAt"`
	UpdatedAt  time.Time      `json:"updatedAt"`
	Replies    []CommentReply `json:"replies"`
}

type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type DocumentState struct {
	Snapshot    []byte
	SnapshotSeq int64
	Updates     [][]byte
}
