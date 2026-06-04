package com.example.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DocumentSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
  private static final Logger log = LoggerFactory.getLogger(DocumentSocketHandler.class);
  static final int MAX_UPDATE_BYTES = 1024 * 1024;
  private static final Pattern UUID_PATTERN =
      Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private final AppRepository repository;
  private final JwtManager jwtManager;
  private final RedisBus redisBus;
  private final MetricsRegistry metrics;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public DocumentSocketHandler(AppRepository repository, JwtManager jwtManager, RedisBus redisBus, MetricsRegistry metrics) {
    this.repository = repository;
    this.jwtManager = jwtManager;
    this.redisBus = redisBus;
    this.metrics = metrics;
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
      claims = jwtManager.verify(websocketToken(session));
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

    DocumentState state;
    try {
      state = repository.loadDocumentState(docId);
    } catch (RuntimeException ex) {
      reject(session, "SYNC_INIT_FAILED", "Could not load document state.");
      return;
    }
    var updates = new ArrayList<String>();
    for (byte[] update : state.updates()) {
      updates.add(Base64.getEncoder().encodeToString(update));
    }
    sessions.computeIfAbsent(docId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    metrics.observeWebSocketConnect();
    var snapshot = state.snapshot() == null || state.snapshot().length == 0 ? null : Base64.getEncoder().encodeToString(state.snapshot());
    session.sendMessage(new TextMessage(mapper.writeValueAsString(new WsMessage("sync:init", docId, null, null, null, null, updates, snapshot, state.snapshotSeq(), null, null))));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    var node = mapper.readTree(message.getPayload());
    var type = node.path("type").asText();
    metrics.observeWebSocketMessage(type);
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

    if ("sync:snapshot".equals(type)) {
      if (!Roles.canEdit(role)) {
        sendError(session, "FORBIDDEN", "You cannot compact this document.");
        return;
      }
      var snapshot = Base64.getDecoder().decode(node.path("snapshot").asText());
      if (snapshot.length > MAX_UPDATE_BYTES) {
        sendError(session, "SNAPSHOT_TOO_LARGE", "Snapshot is too large.");
        return;
      }
      repository.saveSnapshot(docId, node.path("snapshotSeq").asLong(), snapshot);
      return;
    }

    sendError(session, "UNKNOWN_MESSAGE", "Unknown websocket message type.");
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    var docId = (String) session.getAttributes().get("docId");
    if (docId != null && sessions.containsKey(docId)) {
      sessions.get(docId).remove(session);
      metrics.observeWebSocketDisconnect();
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
      log.warn("Comment websocket event broadcast failed for document {}", docId, ignored);
    }
  }

  public void broadcastDocumentRestored(String docId) {
    try {
      var outgoing = mapper.createObjectNode().put("type", "document:restored").put("docId", docId);
      broadcast(docId, outgoing);
    } catch (Exception ex) {
      log.warn("Document restored websocket event broadcast failed for document {}", docId, ex);
    }
  }

  private void broadcastRemote(String docId, JsonNode body) {
    try {
      broadcastLocal(docId, mapper.writeValueAsString(body));
    } catch (Exception ignored) {
      log.warn("Remote websocket broadcast failed for document {}", docId, ignored);
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
    session.sendMessage(new TextMessage(mapper.writeValueAsString(new WsMessage("error", docId, null, null, null, null, null, null, null, code, message))));
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

  @Override
  public List<String> getSubProtocols() {
    return List.of("bearer");
  }

  private String websocketToken(WebSocketSession session) {
    var headers = session.getHandshakeHeaders();
    var protocols = headers == null ? null : headers.get("Sec-WebSocket-Protocol");
    if (protocols != null) {
      for (String header : protocols) {
        var parts = header.split(",");
        for (int index = 0; index < parts.length - 1; index += 1) {
          if ("bearer".equals(parts[index].trim()) && !parts[index + 1].trim().isBlank()) {
            return parts[index + 1].trim();
          }
        }
      }
    }
    return query(session.getUri(), "token");
  }
}
