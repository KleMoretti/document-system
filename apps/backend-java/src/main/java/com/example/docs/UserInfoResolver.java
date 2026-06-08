package com.example.docs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves user display info (email, displayName) for userIds.
 * In ALL role: queries the local users table directly.
 * In DOCUMENT role: calls the auth service via {@link AuthInternalClient}.
 */
@Component
@ConditionalOnRole({ServiceRole.DOCUMENT, ServiceRole.ALL})
public class UserInfoResolver {
  private final ServiceRole role;
  private final JdbcTemplate jdbc;
  private final AuthInternalClient authClient;

  public UserInfoResolver(
      ServiceRole role,
      JdbcTemplate jdbc,
      @Value("${app.auth-base-url:http://localhost:8080}") String authBaseUrl,
      @Value("${app.service-token:}") String serviceToken) {
    this.role = role;
    this.jdbc = jdbc;
    if (role == ServiceRole.DOCUMENT) {
      this.authClient = new AuthInternalClient(authBaseUrl, serviceToken);
    } else {
      this.authClient = null;
    }
  }

  /** Resolve a single user by email. Returns user ID. */
  public String resolveUserIdByEmail(String email) {
    if (role == ServiceRole.DOCUMENT && authClient != null) {
      return authClient.findUserByEmail(email).id();
    }
    // ALL role: query users table directly
    return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, email);
  }

  /** Fill email/displayName into ShareView list. */
  public List<ShareView> fillShareUsers(List<ShareView> shares) {
    var userIds = shares.stream().map(ShareView::userId).collect(Collectors.toSet());
    var users = findUsers(userIds);
    return shares.stream()
        .map(s -> {
          var u = users.get(s.userId());
          return new ShareView(
              s.userId(),
              u != null ? u.email() : null,
              u != null ? u.displayName() : null,
              s.role());
        })
        .toList();
  }

  /** Fill authorName into CommentThread list. */
  public List<CommentThread> fillCommentAuthors(List<CommentThread> comments) {
    var userIds = comments.stream()
        .flatMap(c -> Stream.concat(
            Stream.of(c.authorId()),
            c.replies().stream().map(CommentReply::authorId)))
        .collect(Collectors.toSet());
    var users = findUsers(userIds);
    return comments.stream()
        .map(c -> new CommentThread(
            c.id(), c.documentId(), c.authorId(),
            userName(users, c.authorId()),
            c.body(), c.resolved(), c.createdAt(), c.updatedAt(),
            c.replies().stream()
                .map(r -> new CommentReply(
                    r.id(), r.commentId(), r.authorId(),
                    userName(users, r.authorId()),
                    r.body(), r.createdAt()))
                .toList()))
        .toList();
  }

  private String userName(Map<String, User> users, String userId) {
    var u = users.get(userId);
    return u != null ? u.displayName() : null;
  }

  private Map<String, User> findUsers(Set<String> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    if (role == ServiceRole.DOCUMENT && authClient != null) {
      return authClient.findUsersByIds(List.copyOf(userIds));
    }
    // ALL role: query users table directly
    var placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
    var results = jdbc.query(
        "SELECT id, email, display_name, created_at FROM users WHERE id IN (" + placeholders + ")",
        (rs, row) -> new User(
            rs.getString("id"),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant()),
        userIds.toArray());
    return results.stream().collect(Collectors.toMap(User::id, u -> u));
  }
}
