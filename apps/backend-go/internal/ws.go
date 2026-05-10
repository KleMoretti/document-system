package internal

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"strings"
	"sync"

	"github.com/gorilla/websocket"
)

type WSMessage struct {
	Type        string   `json:"type"`
	DocID       string   `json:"docId"`
	UserID      string   `json:"userId,omitempty"`
	DisplayName string   `json:"displayName,omitempty"`
	Color       string   `json:"color,omitempty"`
	Update      string   `json:"update,omitempty"`
	Updates     []string `json:"updates,omitempty"`
	Code        string   `json:"code,omitempty"`
	Message     string   `json:"message,omitempty"`
}

type Client struct {
	docID  string
	userID string
	conn   *websocket.Conn
	send   chan []byte
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
	close(client.send)
}

func (h *Hub) BroadcastRaw(docID string, payload []byte) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for client := range h.clients[docID] {
		select {
		case client.send <- payload:
		default:
		}
	}
}

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

func (s *Server) websocket(w http.ResponseWriter, r *http.Request) {
	docID := strings.TrimPrefix(r.URL.Path, "/ws/documents/")
	token := r.URL.Query().Get("token")
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

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	client := &Client{docID: docID, userID: claims.UserID, conn: conn, send: make(chan []byte, 32)}
	s.hub.Register(client)
	defer s.hub.Unregister(client)

	updates, err := s.store.LoadUpdates(r.Context(), docID)
	if err == nil {
		encoded := make([]string, 0, len(updates))
		for _, update := range updates {
			encoded = append(encoded, base64.StdEncoding.EncodeToString(update))
		}
		_ = conn.WriteJSON(WSMessage{Type: "sync:init", DocID: docID, Updates: encoded})
	}

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
	payload, _ := json.Marshal(msg)
	s.hub.BroadcastRaw(msg.DocID, payload)
	s.bus.Publish(context.Background(), msg.DocID, payload)
}

func sendError(client *Client, code, message string) {
	payload, _ := json.Marshal(WSMessage{Type: "error", DocID: client.docID, Code: code, Message: message})
	client.send <- payload
}
