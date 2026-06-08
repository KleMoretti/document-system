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
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@ConditionalOnRole({ServiceRole.REALTIME, ServiceRole.ALL})
public class DocumentSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
  private static final Logger log = LoggerFactory.getLogger(DocumentSocketHandler.class);
  static final int MAX_UPDATE_BYTES = 1024 * 1024;
  private static final Pattern UUID_PATTERN =
      Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private final RealtimeRepository repository;
  private final JwtManager jwtManager;
  private final RedisBus redisBus;
  private final MetricsRegistry metrics;
  private final UpdateBatcher updateBatcher;
  private final int sendQueueSize;
  private final int snapshotMinUpdates;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Set<OutboundWebSocketClient>> sessions = new ConcurrentHashMap<>();
  private final Map<String, OutboundWebSocketClient> clientsBySession = new ConcurrentHashMap<>();

  public DocumentSocketHandler(RealtimeRepository repository, JwtManager jwtManager, RedisBus redisBus, MetricsRegistry metrics) {
    this(repository, jwtManager, redisBus, metrics, new UpdateBatcher(repository, metrics, 25, 32), 32, 100);
  }

  @Autowired
  public DocumentSocketHandler(
      RealtimeRepository repository,
      JwtManager jwtManager,
      RedisBus redisBus,
      MetricsRegistry metrics,
      UpdateBatcher updateBatcher,
      @Value("${app.ws-send-queue-size:32}") int sendQueueSize,
      @Value("${app.ws-snapshot-min-updates:100}") int snapshotMinUpdates) {
    this.repository = repository;
    this.jwtManager = jwtManager;
    this.redisBus = redisBus;
    this.metrics = metrics;
    this.updateBatcher = updateBatcher;
    this.sendQueueSize = Math.max(1, sendQueueSize);
    this.snapshotMinUpdates = Math.max(1, snapshotMinUpdates);
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
    var client = new OutboundWebSocketClient(session, sendQueueSize, metrics);
    var snapshot = state.snapshot() == null || state.snapshot().length == 0 ? null : Base64.getEncoder().encodeToString(state.snapshot());
    client.enqueue(mapper.writeValueAsString(new WsMessage("sync:init", docId, null, null, null, null, updates, snapshot, state.snapshotSeq(), null, null)));
    client.start();
    sessions.computeIfAbsent(docId, ignored -> ConcurrentHashMap.newKeySet()).add(client);
    clientsBySession.put(session.getId(), client);
    metrics.observeWebSocketConnect();
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
      try {
        updateBatcher.append(docId, update).join();
      } catch (CompletionException ex) {
        sendError(session, "DATABASE_ERROR", "Could not persist update.");
        return;
      }
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
      byte[] snapshot;
      try {
        snapshot = Base64.getDecoder().decode(node.path("snapshot").asText());
      } catch (IllegalArgumentException ex) {
        sendError(session, "INVALID_SNAPSHOT", "Snapshot must be base64 encoded.");
        return;
      }
      if (snapshot.length > MAX_UPDATE_BYTES) {
        sendError(session, "SNAPSHOT_TOO_LARGE", "Snapshot is too large.");
        return;
      }
      var state = repository.loadDocumentState(docId);
      if (!UpdateBatcher.snapshotAllowed(
          node.path("snapshotSeq").asLong(), state.snapshotSeq(), state.updates().size(), snapshotMinUpdates)) {
        sendError(session, "SNAPSHOT_NOT_USEFUL", "Snapshot is stale or does not compact enough updates.");
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
    var client = clientsBySession.remove(session.getId());
    if (docId != null && client != null && sessions.containsKey(docId)) {
      sessions.get(docId).remove(client);
      client.stop();
      metrics.observeWebSocketDisconnect();
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
      log.warn("Remote websocket broadcast failed for document {}", docId, ignored);
    }
  }

  private void broadcastLocal(String docId, String text) throws Exception {
    var startedAt = System.nanoTime();
    for (OutboundWebSocketClient target : sessions.getOrDefault(docId, Set.of())) {
      if (target.session().isOpen()) {
        target.enqueue(text);
      }
    }
    metrics.observeWebSocketBroadcast(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    metrics.observeWebSocketBytes(text.length());
  }

  private void sendError(WebSocketSession session, String code, String message) throws Exception {
    var docId = (String) session.getAttributes().get("docId");
    metrics.observeWebSocketError(code);
    var text = mapper.writeValueAsString(new WsMessage("error", docId, null, null, null, null, null, null, null, code, message));
    var sessionId = session.getId();
    var client = sessionId == null ? null : clientsBySession.get(sessionId);
    if (client != null) {
      client.enqueue(text);
      return;
    }
    session.sendMessage(new TextMessage(text));
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
