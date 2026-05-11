package internal

import (
	"context"
	"encoding/json"
	"net/http"
	"reflect"
	"strings"

	"golang.org/x/crypto/bcrypt"
)

type Server struct {
	cfg   Config
	store *Store
	auth  *JWTManager
	hub   *Hub
	bus   *RedisBus
}

func NewServer(cfg Config, store *Store, auth *JWTManager, hub *Hub, bus *RedisBus) *Server {
	return &Server{cfg: cfg, store: store, auth: auth, hub: hub, bus: bus}
}

func (s *Server) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/auth/register", s.withCORS(s.register))
	mux.HandleFunc("/api/auth/login", s.withCORS(s.login))
	mux.HandleFunc("/api/me", s.withCORS(s.requireAuth(s.me)))
	mux.HandleFunc("/api/documents", s.withCORS(s.requireAuth(s.documents)))
	mux.HandleFunc("/api/documents/", s.withCORS(s.requireAuth(s.documentByID)))
	mux.HandleFunc("/ws/documents/", s.websocket)
	return mux
}

func (s *Server) register(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		return
	}
	var req RegisterRequest
	if !decode(w, r, &req) {
		return
	}
	if req.Email == "" || req.Password == "" || req.DisplayName == "" {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Email, password and displayName are required.")
		return
	}
	user, _, err := s.store.CreateUser(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusConflict, "USER_EXISTS", "User already exists or cannot be created.")
		return
	}
	token, err := s.auth.Sign(UserClaims{UserID: user.ID, Email: user.Email})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "TOKEN_ERROR", "Could not create token.")
		return
	}
	writeJSON(w, http.StatusCreated, AuthResponse{Token: token, User: user})
}

func (s *Server) login(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		return
	}
	var req LoginRequest
	if !decode(w, r, &req) {
		return
	}
	user, hash, err := s.store.FindUserForLogin(r.Context(), req.Email)
	if err != nil || bcrypt.CompareHashAndPassword([]byte(hash), []byte(req.Password)) != nil {
		writeError(w, http.StatusUnauthorized, "INVALID_CREDENTIALS", "Email or password is incorrect.")
		return
	}
	token, err := s.auth.Sign(UserClaims{UserID: user.ID, Email: user.Email})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "TOKEN_ERROR", "Could not create token.")
		return
	}
	writeJSON(w, http.StatusOK, AuthResponse{Token: token, User: user})
}

func (s *Server) me(w http.ResponseWriter, r *http.Request, claims UserClaims) {
	user, err := s.store.FindUser(r.Context(), claims.UserID)
	if err != nil {
		writeError(w, http.StatusNotFound, "NOT_FOUND", "User not found.")
		return
	}
	writeJSON(w, http.StatusOK, user)
}

func (s *Server) documents(w http.ResponseWriter, r *http.Request, claims UserClaims) {
	switch r.Method {
	case http.MethodGet:
		docs, err := s.store.ListDocumentsFiltered(r.Context(), claims.UserID, r.URL.Query().Get("query"), r.URL.Query().Get("status"))
		if err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not list documents.")
			return
		}
		writeJSON(w, http.StatusOK, docs)
	case http.MethodPost:
		var req CreateDocumentRequest
		if !decode(w, r, &req) {
			return
		}
		if req.Title == "" {
			req.Title = "Untitled document"
		}
		doc, err := s.store.CreateDocument(r.Context(), claims.UserID, req.Title)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not create document.")
			return
		}
		writeJSON(w, http.StatusCreated, doc)
	default:
		writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
	}
}

func (s *Server) documentByID(w http.ResponseWriter, r *http.Request, claims UserClaims) {
	rest := strings.TrimPrefix(r.URL.Path, "/api/documents/")
	parts := strings.Split(strings.Trim(rest, "/"), "/")
	if len(parts) == 0 || parts[0] == "" {
		writeError(w, http.StatusNotFound, "NOT_FOUND", "Document not found.")
		return
	}
	docID := parts[0]
	if len(parts) == 2 && parts[1] == "restore" {
		doc, err := s.store.GetDocumentIncludingDeleted(r.Context(), claims.UserID, docID)
		if err != nil {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "Document not found.")
			return
		}
		s.handleRestore(w, r, doc)
		return
	}
	doc, err := s.store.GetDocument(r.Context(), claims.UserID, docID)
	if err != nil {
		writeError(w, http.StatusNotFound, "NOT_FOUND", "Document not found.")
		return
	}

	if len(parts) == 1 {
		s.handleDocument(w, r, doc, claims)
		return
	}
	if parts[1] == "shares" {
		s.handleShares(w, r, doc, parts)
		return
	}
	if parts[1] == "versions" {
		s.handleVersions(w, r, doc, parts, claims)
		return
	}
	if parts[1] == "comments" {
		s.handleComments(w, r, doc, parts, claims)
		return
	}
	writeError(w, http.StatusNotFound, "NOT_FOUND", "Route not found.")
}

func (s *Server) handleRestore(w http.ResponseWriter, r *http.Request, doc Document) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		return
	}
	if doc.Role != "owner" {
		writeError(w, http.StatusForbidden, "FORBIDDEN", "Only the owner can restore this document.")
		return
	}
	if err := s.store.RestoreDocument(r.Context(), doc.ID); err != nil {
		writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not restore document.")
		return
	}
	doc.DeletedAt = nil
	writeJSON(w, http.StatusOK, doc)
}

func (s *Server) handleDocument(w http.ResponseWriter, r *http.Request, doc Document, claims UserClaims) {
	switch r.Method {
	case http.MethodGet:
		writeJSON(w, http.StatusOK, doc)
	case http.MethodPatch:
		if !CanEdit(doc.Role) {
			writeError(w, http.StatusForbidden, "FORBIDDEN", "You cannot rename this document.")
			return
		}
		var req RenameDocumentRequest
		if !decode(w, r, &req) {
			return
		}
		if req.Title == "" {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Title is required.")
			return
		}
		if err := s.store.RenameDocument(r.Context(), claims.UserID, doc.ID, req.Title); err != nil {
			if err == ErrForbidden {
				writeError(w, http.StatusForbidden, "FORBIDDEN", "You cannot rename this document.")
				return
			}
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not rename document.")
			return
		}
		doc.Title = req.Title
		writeJSON(w, http.StatusOK, doc)
	case http.MethodDelete:
		if doc.Role != "owner" {
			writeError(w, http.StatusForbidden, "FORBIDDEN", "Only the owner can delete this document.")
			return
		}
		if err := s.store.DeleteDocument(r.Context(), doc.ID); err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not delete document.")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	default:
		writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
	}
	_ = claims
}

func (s *Server) handleShares(w http.ResponseWriter, r *http.Request, doc Document, parts []string) {
	if !CanShare(doc.Role) {
		writeError(w, http.StatusForbidden, "FORBIDDEN", "Only the owner can manage sharing.")
		return
	}
	if len(parts) == 2 {
		switch r.Method {
		case http.MethodGet:
			shares, err := s.store.ListShares(r.Context(), doc.ID)
			if err != nil {
				writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not list shares.")
				return
			}
			writeJSON(w, http.StatusOK, shares)
		case http.MethodPost:
			var req ShareDocumentRequest
			if !decode(w, r, &req) {
				return
			}
			if err := s.store.ShareDocument(r.Context(), doc.ID, req); err != nil {
				if err == ErrUserNotFound {
					writeError(w, http.StatusNotFound, "USER_NOT_FOUND", "User not found.")
					return
				}
				writeError(w, http.StatusBadRequest, "SHARE_ERROR", "Could not share document.")
				return
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		}
		return
	}
	if len(parts) == 3 && r.Method == http.MethodDelete {
		if err := s.store.RemoveShare(r.Context(), doc.ID, parts[2]); err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not remove share.")
			return
		}
		w.WriteHeader(http.StatusNoContent)
		return
	}
	writeError(w, http.StatusNotFound, "NOT_FOUND", "Route not found.")
}

func (s *Server) handleVersions(w http.ResponseWriter, r *http.Request, doc Document, parts []string, claims UserClaims) {
	if len(parts) == 2 {
		switch r.Method {
		case http.MethodGet:
			versions, err := s.store.ListVersions(r.Context(), doc.ID)
			if err != nil {
				writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not list versions.")
				return
			}
			writeJSON(w, http.StatusOK, versions)
		case http.MethodPost:
			var req CreateVersionRequest
			if !decode(w, r, &req) {
				return
			}
			version, err := s.store.CreateVersion(r.Context(), doc.ID, claims.UserID, req.Label)
			if err != nil {
				writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not create version.")
				return
			}
			writeJSON(w, http.StatusCreated, version)
		default:
			writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		}
		return
	}
	if len(parts) == 3 && r.Method == http.MethodGet {
		version, err := s.store.GetVersion(r.Context(), doc.ID, parts[2])
		if err != nil {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "Version not found.")
			return
		}
		writeJSON(w, http.StatusOK, version)
		return
	}
	if len(parts) == 4 && parts[3] == "restore" && r.Method == http.MethodPost {
		if !CanEdit(doc.Role) {
			writeError(w, http.StatusForbidden, "FORBIDDEN", "You cannot restore a version for this document.")
			return
		}
		if err := s.store.RestoreVersion(r.Context(), doc.ID, parts[2]); err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not restore version.")
			return
		}
		w.WriteHeader(http.StatusNoContent)
		return
	}
	writeError(w, http.StatusNotFound, "NOT_FOUND", "Route not found.")
}

func (s *Server) handleComments(w http.ResponseWriter, r *http.Request, doc Document, parts []string, claims UserClaims) {
	if len(parts) == 2 {
		switch r.Method {
		case http.MethodGet:
			comments, err := s.store.ListComments(r.Context(), doc.ID)
			if err != nil {
				writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not list comments.")
				return
			}
			writeJSON(w, http.StatusOK, comments)
		case http.MethodPost:
			var req CreateCommentRequest
			if !decode(w, r, &req) {
				return
			}
			if strings.TrimSpace(req.Body) == "" {
				writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Comment body is required.")
				return
			}
			comment, err := s.store.CreateComment(r.Context(), doc.ID, claims.UserID, strings.TrimSpace(req.Body))
			if err != nil {
				writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not create comment.")
				return
			}
			broadcast(s, WSMessage{Type: "comment:created", DocID: doc.ID, CommentID: comment.ID, Comment: &comment})
			writeJSON(w, http.StatusCreated, comment)
		default:
			writeError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "Method not allowed.")
		}
		return
	}
	if len(parts) == 4 && parts[3] == "replies" && r.Method == http.MethodPost {
		var req CreateReplyRequest
		if !decode(w, r, &req) {
			return
		}
		if strings.TrimSpace(req.Body) == "" {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Reply body is required.")
			return
		}
		comment, err := s.store.AddReply(r.Context(), doc.ID, parts[2], claims.UserID, strings.TrimSpace(req.Body))
		if err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not create reply.")
			return
		}
		broadcast(s, WSMessage{Type: "comment:updated", DocID: doc.ID, CommentID: comment.ID, Comment: &comment})
		writeJSON(w, http.StatusCreated, comment)
		return
	}
	if len(parts) == 3 && r.Method == http.MethodPatch {
		if !CanEdit(doc.Role) {
			writeError(w, http.StatusForbidden, "FORBIDDEN", "Only editors can update comments.")
			return
		}
		var req UpdateCommentRequest
		if !decode(w, r, &req) {
			return
		}
		comment, err := s.store.UpdateComment(r.Context(), doc.ID, parts[2], req)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "DATABASE_ERROR", "Could not update comment.")
			return
		}
		eventType := "comment:updated"
		if req.Resolved != nil {
			eventType = "comment:resolved"
		}
		broadcast(s, WSMessage{Type: eventType, DocID: doc.ID, CommentID: comment.ID, Comment: &comment})
		writeJSON(w, http.StatusOK, comment)
		return
	}
	writeError(w, http.StatusNotFound, "NOT_FOUND", "Route not found.")
}

func (s *Server) requireAuth(next func(http.ResponseWriter, *http.Request, UserClaims)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if !strings.HasPrefix(authHeader, "Bearer ") {
			writeError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Missing bearer token.")
			return
		}
		claims, err := s.auth.Verify(strings.TrimPrefix(authHeader, "Bearer "))
		if err != nil {
			writeError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Invalid token.")
			return
		}
		next(w, r, claims)
	}
}

func (s *Server) withCORS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if origin := r.Header.Get("Origin"); origin != "" && originAllowed(r, s.cfg.AllowedOrigins) {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
		}
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next(w, r)
	}
}

func decode(w http.ResponseWriter, r *http.Request, target any) bool {
	if err := json.NewDecoder(r.Body).Decode(target); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", "Request body must be valid JSON.")
		return false
	}
	return true
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(normalizeJSONValue(value))
}

func normalizeJSONValue(value any) any {
	if value == nil {
		return value
	}
	reflected := reflect.ValueOf(value)
	if reflected.Kind() == reflect.Slice && reflected.IsNil() {
		return reflect.MakeSlice(reflected.Type(), 0, 0).Interface()
	}
	return value
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, APIError{Code: code, Message: message})
}

type contextKey string

func withClaims(ctx context.Context, claims UserClaims) context.Context {
	return context.WithValue(ctx, contextKey("claims"), claims)
}
