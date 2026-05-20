package internal

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"

	"github.com/gorilla/websocket"
)

type WSMessage struct {
	Type        string         `json:"type"`
	DocID       string         `json:"docId"`
	UserID      string         `json:"userId,omitempty"`
	DisplayName string         `json:"displayName,omitempty"`
	Color       string         `json:"color,omitempty"`
	Update      string         `json:"update,omitempty"`
	Updates     []string       `json:"updates,omitempty"`
	CommentID   string         `json:"commentId,omitempty"`
	Comment     *CommentThread `json:"comment,omitempty"`
	Code        string         `json:"code,omitempty"`
	Message     string         `json:"message,omitempty"`
}

const maxUpdateBytes = 1024 * 1024

type Client struct {
	docID     string
	userID    string
	conn      *websocket.Conn
	send      chan []byte
	closeOnce sync.Once
}

type Hub struct {
	instanceID string
	mu         sync.RWMutex
	clients    map[string]map[*Client]struct{}
}

func NewHub(instanceID string) *Hub {
	return &Hub{instanceID: instanceID, clients: map[string]map[*Client]struct{}{}}
}

func (h *Hub) Register(client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.clients[client.docID] == nil {
		h.clients[client.docID] = map[*Client]struct{}{}
	}
	h.clients[client.docID][client] = struct{}{}
}

func (h *Hub) Unregister(client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.clients[client.docID], client)
	client.closeOnce.Do(func() {
		close(client.send)
	})
}

func (h *Hub) BroadcastRaw(docID string, payload []byte) {
	var slowClients []*Client
	h.mu.RLock()
	for client := range h.clients[docID] {
		select {
		case client.send <- payload:
		default:
			slowClients = append(slowClients, client)
		}
	}
	h.mu.RUnlock()
	for _, client := range slowClients {
		notifySlowClient(client)
		h.Unregister(client)
		if client.conn != nil {
			_ = client.conn.Close()
		}
	}
}

func (s *Server) websocket(w http.ResponseWriter, r *http.Request) {
	docID := strings.TrimPrefix(r.URL.Path, "/ws/documents/")
	if !validID(docID) {
		http.Error(w, "invalid document id", http.StatusBadRequest)
		return
	}
	token := websocketToken(r)
	claims, err := s.auth.Verify(token)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}
	role, err := s.store.GetRole(r.Context(), claims.UserID, docID)
	if err != nil {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}

	upgrader := websocket.Upgrader{
		Subprotocols: []string{"bearer"},
		CheckOrigin: func(r *http.Request) bool {
			return originAllowed(r, s.cfg.AllowedOrigins)
		},
	}
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	updates, err := s.store.LoadUpdates(r.Context(), docID)
	if err != nil {
		_ = conn.WriteJSON(WSMessage{Type: "error", DocID: docID, Code: "SYNC_INIT_FAILED", Message: "Could not load document state."})
		_ = conn.Close()
		return
	}
	client := &Client{docID: docID, userID: claims.UserID, conn: conn, send: make(chan []byte, 32)}
	s.hub.Register(client)
	s.metrics.ObserveWebSocketConnect()
	defer func() {
		s.hub.Unregister(client)
		s.metrics.ObserveWebSocketDisconnect()
	}()

	encoded := make([]string, 0, len(updates))
	for _, update := range updates {
		encoded = append(encoded, base64.StdEncoding.EncodeToString(update))
	}
	_ = conn.WriteJSON(WSMessage{Type: "sync:init", DocID: docID, Updates: encoded})

	go writePump(client)
	readPump(r.Context(), s, client, role)
}

func writePump(client *Client) {
	for payload := range client.send {
		if err := client.conn.WriteMessage(websocket.TextMessage, payload); err != nil {
			return
		}
	}
}

func readPump(ctx context.Context, s *Server, client *Client, role string) {
	defer client.conn.Close()
	for {
		var msg WSMessage
		if err := client.conn.ReadJSON(&msg); err != nil {
			return
		}
		msg.DocID = client.docID
		msg.UserID = client.userID
		s.metrics.ObserveWebSocketMessage(msg.Type)

		switch msg.Type {
		case "sync:update":
			if !CanEdit(role) {
				sendError(client, "FORBIDDEN", "You cannot edit this document.")
				continue
			}
			update, err := base64.StdEncoding.DecodeString(msg.Update)
			if err != nil {
				sendError(client, "INVALID_UPDATE", "Update must be base64 encoded.")
				continue
			}
			if !updateAllowed(update) {
				sendError(client, "UPDATE_TOO_LARGE", "Update is too large.")
				continue
			}
			if err := s.store.AppendUpdate(ctx, client.docID, update); err != nil {
				sendError(client, "DATABASE_ERROR", "Could not persist update.")
				continue
			}
			broadcast(s, msg)
		case "presence:update":
			broadcast(s, msg)
		default:
			sendError(client, "UNKNOWN_MESSAGE", "Unknown websocket message type.")
		}
	}
}

func broadcast(s *Server, msg WSMessage) {
	payload, err := json.Marshal(msg)
	if err != nil {
		log.Printf("websocket broadcast marshal failed: %v", err)
		return
	}
	s.hub.BroadcastRaw(msg.DocID, payload)
	s.bus.Publish(context.Background(), msg.DocID, payload)
}

func sendError(client *Client, code, message string) {
	payload, _ := json.Marshal(WSMessage{Type: "error", DocID: client.docID, Code: code, Message: message})
	select {
	case client.send <- payload:
	default:
	}
}

func notifySlowClient(client *Client) {
	payload, _ := json.Marshal(WSMessage{Type: "error", DocID: client.docID, Code: "SLOW_CLIENT", Message: "Connection closed because the client is not keeping up."})
	select {
	case client.send <- payload:
		return
	default:
	}
	select {
	case <-client.send:
	default:
	}
	select {
	case client.send <- payload:
	default:
	}
}

func updateAllowed(update []byte) bool {
	return len(update) <= maxUpdateBytes
}

var idPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

func validID(value string) bool {
	return idPattern.MatchString(value)
}

func originAllowed(r *http.Request, allowedOrigins string) bool {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return true
	}
	parsedOrigin, err := url.Parse(origin)
	if err != nil || parsedOrigin.Scheme == "" || parsedOrigin.Host == "" {
		return false
	}
	for _, allowed := range strings.Split(allowedOrigins, ",") {
		if strings.TrimSpace(allowed) == origin {
			return true
		}
	}
	return false
}

func websocketToken(r *http.Request) string {
	protocols := strings.Split(r.Header.Get("Sec-WebSocket-Protocol"), ",")
	for index, protocol := range protocols {
		if strings.TrimSpace(protocol) == "bearer" && index+1 < len(protocols) {
			if token := strings.TrimSpace(protocols[index+1]); token != "" {
				return token
			}
		}
	}
	return r.URL.Query().Get("token")
}
