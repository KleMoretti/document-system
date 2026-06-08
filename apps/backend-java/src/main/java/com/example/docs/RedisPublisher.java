package com.example.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

/**
 * Publishes document events to Redis for cross-service communication.
 * Used by the document (REST) service; consumed by the realtime (WebSocket) service.
 */
@Component
@ConditionalOnRole({ServiceRole.DOCUMENT, ServiceRole.ALL})
public class RedisPublisher {
  private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);
  private final JedisPooled jedis;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String source = UUID.randomUUID().toString();

  public RedisPublisher(JedisPooled jedis) {
    this.jedis = jedis;
  }

  public void publishCommentEvent(String docId, String type, CommentThread comment) {
    try {
      var body = mapper.createObjectNode();
      body.put("type", type);
      body.put("docId", docId);
      body.put("commentId", comment.id());
      body.set("comment", mapper.valueToTree(comment));
      publish(docId, body);
    } catch (Exception ex) {
      log.warn("Comment Redis publish failed for document {}", docId, ex);
    }
  }

  public void publishDocumentRestored(String docId) {
    try {
      var body = mapper.createObjectNode().put("type", "document:restored").put("docId", docId);
      publish(docId, body);
    } catch (Exception ex) {
      log.warn("Document restored Redis publish failed for document {}", docId, ex);
    }
  }

  private void publish(String docId, ObjectNode body) {
    try {
      var envelope = mapper.createObjectNode();
      envelope.put("source", source);
      envelope.put("docId", docId);
      envelope.set("body", body);
      jedis.publish("doc:" + docId, mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      log.warn("Redis publish failed", ex);
    }
  }
}
