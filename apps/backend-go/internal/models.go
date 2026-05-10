package internal

import "time"

type User struct {
	ID          string    `json:"id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"displayName"`
	CreatedAt   time.Time `json:"createdAt"`
}

type Document struct {
	ID        string    `json:"id"`
	Title     string    `json:"title"`
	OwnerID   string    `json:"ownerId"`
	Role      string    `json:"role"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
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

type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
