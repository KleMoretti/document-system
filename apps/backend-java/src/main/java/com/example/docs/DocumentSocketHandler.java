package com.example.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DocumentSocketHandler extends TextWebSocketHandler {
  static final int MAX_UPDATE_BYTES = 1024 * 1024;
  private static final Pattern UUID_PATTERN =
      Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
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
    String docId;
    try {
      docId = docId(session);
    } catch (RuntimeException ex) {
      reject(session, "INVALID_DOCUMENT_ID", "Document id must be a UUID.");
      return;
    }
    session.getAttributes().put("docId", docId);
    UserClaims claims;
    String role;
    try {
      claims = jwtManager.verify(query(session.getUri(), "token"));
    } catch (RuntimeException ex) {
      reject(session, "UNAUTHORIZED", "Invalid or missing token.");
      return;
    }
    try {
      role = repository.getRole(claims.userId(), docId);
    } catch (RuntimeException ex) {
      reject(session, "FORBIDDEN", "You cannot access this document.");
      return;
    }
    session.getAttributes().put("userId", claims.userId());
    session.getAttributes().put("role", role);

    var updates = new ArrayList<String>();
    try {
      for (byte[] update : repository.loadUpdates(docId)) {
        updates.add(Base64.getEncoder().encodeToString(update));
      }
    } catch (RuntimeException ex) {
      reject(session, "SYNC_INIT_FAILED", "Could not load document state.");
      return;
    }
    sessions.computeIfAbsent(docId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
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
      var update = Base64.getDecoder().decode(encoded);
      if (update.length > MAX_UPDATE_BYTES) {
        sendError(session, "UPDATE_TOO_LARGE", "Update is too large.");
        return;
      }
      repository.appendUpdate(docId, update);
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

  public void broadcastCommentEvent(String docId, String type, CommentThread comment) {
    try {
      var outgoing =
          mapper.createObjectNode()
              .put("type", type)
              .put("docId", docId)
              .put("commentId", comment.id())
              .set("comment", mapper.valueToTree(comment));
      broadcast(docId, outgoing);
    } catch (Exception ignored) {
    }
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

  private void reject(WebSocketSession session, String code, String message) throws Exception {
    if (session.isOpen()) {
      sendError(session, code, message);
      session.close(CloseStatus.NOT_ACCEPTABLE);
    }
  }

  private String docId(WebSocketSession session) {
    var path = session.getUri().getPath();
    var id = path.substring(path.lastIndexOf('/') + 1);
    if (!UUID_PATTERN.matcher(id).matches()) {
      throw new BadRequestException("Document id must be a UUID.");
    }
    return id;
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
