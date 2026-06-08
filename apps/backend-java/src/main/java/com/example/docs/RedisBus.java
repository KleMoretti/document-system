package com.example.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisPubSub;

/**
 * Redis pub/sub bus for realtime service.
 * Subscribes to doc:* channels and forwards remote events to local
 * WebSocket connections. Also publishes local sync:update / presence:update
 * events so other realtime instances can relay them.
 */
@Component
@ConditionalOnRole({ServiceRole.REALTIME, ServiceRole.ALL})
public class RedisBus {
  private static final Logger log = LoggerFactory.getLogger(RedisBus.class);
  private final JedisPooled jedis;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String source = UUID.randomUUID().toString();
  private BiConsumer<String, JsonNode> consumer = (docId, body) -> {};

  public RedisBus(JedisPooled jedis) {
    this.jedis = jedis;
    Thread.ofVirtual().start(this::subscribe);
  }

  public void attach(BiConsumer<String, JsonNode> consumer) {
    this.consumer = consumer;
  }

  /** Publish a sync/presence message from local WebSocket to other realtime instances. */
  public void publish(String docId, JsonNode body) {
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

  private void subscribe() {
    try {
      jedis.psubscribe(
          new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
              try {
                var node = mapper.readTree(message);
                if (source.equals(node.path("source").asText())) {
                  return;
                }
                consumer.accept(node.path("docId").asText(), node.path("body"));
              } catch (Exception ex) {
                log.warn("Redis payload ignored", ex);
              }
            }
          },
          "doc:*");
    } catch (Exception ex) {
      log.warn("Redis subscription stopped", ex);
    }
  }
}
