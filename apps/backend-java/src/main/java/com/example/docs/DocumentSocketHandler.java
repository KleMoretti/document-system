package com.example.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DocumentSocketHandler extends TextWebSocketHandler {
  private final AppRepository repository;
  private final JwtManager jwtManager;
  private final RedisBus redisBus;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public DocumentSocketHandler(AppRepository repository, JwtManager jwtManager, RedisBus redisBus) {
    this.repository = repository;
    this.jwtManager = jwtManager;
    this.redisBus = redisBus;
    this.redisBus.attach(this::broadcastRemote);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    var docId = docId(session);
    var claims = jwtManager.verify(query(session.getUri(), "token"));
    var role = repository.getRole(claims.userId(), docId);
    session.getAttributes().put("docId", docId);
    session.getAttributes().put("userId", claims.userId());
    session.getAttributes().put("role", role);
    sessions.computeIfAbsent(docId, ignored -> ConcurrentHashMap.newKeySet()).add(session);

    var updates = new ArrayList<String>();
    for (byte[] update : repository.loadUpdates(docId)) {
      updates.add(Base64.getEncoder().encodeToString(update));
    }
    session.sendMessage(new TextMessage(mapper.writeValueAsString(new WsMessage("sync:init", docId, null, null, null, null, updates, null, null))));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    var node = mapper.readTree(message.getPayload());
    var type = node.path("type").asText();
    var docId = (String) session.getAttributes().get("docId");
    var userId = (String) session.getAttributes().get("userId");
    var role = (String) session.getAttributes().get("role");

    if ("sync:update".equals(type)) {
      if (!Roles.canEdit(role)) {
        sendError(session, "FORBIDDEN", "You cannot edit this document.");
        return;
      }
      var encoded = node.path("update").asText();
      repository.appendUpdate(docId, Base64.getDecoder().decode(encoded));
      var outgoing =
          mapper.createObjectNode()
              .put("type", "sync:update")
              .put("docId", docId)
              .put("userId", userId)
              .put("update", encoded);
      broadcast(docId, outgoing);
      return;
    }

    if ("presence:update".equals(type)) {
      var outgoing = (com.fasterxml.jackson.databind.node.ObjectNode) node.deepCopy();
      outgoing.put("docId", docId);
      outgoing.put("userId", userId);
      broadcast(docId, outgoing);
      return;
    }

    sendError(session, "UNKNOWN_MESSAGE", "Unknown websocket message type.");
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    var docId = (String) session.getAttributes().get("docId");
    if (docId != null && sessions.containsKey(docId)) {
      sessions.get(docId).remove(session);
    }
  }

  private void broadcast(String docId, JsonNode body) throws Exception {
    var text = mapper.writeValueAsString(body);
    broadcastLocal(docId, text);
    redisBus.publish(docId, body);
  }

  private void broadcastRemote(String docId, JsonNode body) {
    try {
      broadcastLocal(docId, mapper.writeValueAsString(body));
    } catch (Exception ignored) {
    }
  }

  private void broadcastLocal(String docId, String text) throws Exception {
    for (WebSocketSession target : sessions.getOrDefault(docId, Set.of())) {
      if (target.isOpen()) {
        target.sendMessage(new TextMessage(text));
      }
    }
  }

  private void sendError(WebSocketSession session, String code, String message) throws Exception {
    var docId = (String) session.getAttributes().get("docId");
    session.sendMessage(new TextMessage(mapper.writeValueAsString(new WsMessage("error", docId, null, null, null, null, null, code, message))));
  }

  private String docId(WebSocketSession session) {
    var path = session.getUri().getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private String query(URI uri, String key) {
    if (uri.getQuery() == null) {
      throw new UnauthorizedException("Missing token.");
    }
    for (String part : uri.getQuery().split("&")) {
      var split = part.split("=", 2);
      if (split.length == 2 && key.equals(split[0])) {
        return split[1];
      }
    }
    throw new UnauthorizedException("Missing token.");
  }
}
