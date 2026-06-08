package com.example.docs;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@ConditionalOnRole({ServiceRole.DOCUMENT, ServiceRole.ALL})
public class DocumentController {
  private final DocumentRepository repository;
  private final BearerTokenVerifier verifier;
  private final UserInfoResolver userInfo;
  private final RedisPublisher redisPublisher;

  public DocumentController(
      DocumentRepository repository,
      BearerTokenVerifier verifier,
      UserInfoResolver userInfo,
      RedisPublisher redisPublisher) {
    this.repository = repository;
    this.verifier = verifier;
    this.userInfo = userInfo;
    this.redisPublisher = redisPublisher;
  }

  @GetMapping
  List<DocumentView> list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "active") String status) {
    var claims = verifier.claims(authorization);
    return repository.listDocuments(claims.userId(), query, status);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DocumentView create(
      @RequestHeader("Authorization") String authorization, @RequestBody CreateDocumentRequest req) {
    var claims = verifier.claims(authorization);
    var title = req.title() == null || req.title().isBlank() ? "Untitled document" : req.title();
    return repository.createDocument(claims.userId(), title);
  }

  @GetMapping("/{docId}")
  DocumentView get(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    var claims = verifier.claims(authorization);
    return repository.getDocument(claims.userId(), docId);
  }

  @PatchMapping("/{docId}")
  DocumentView rename(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @RequestBody RenameDocumentRequest req) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canEdit(doc.role())) {
      throw new ForbiddenException("You cannot rename this document.");
    }
    repository.renameDocument(claims.userId(), docId, req.title());
    return repository.getDocument(claims.userId(), docId);
  }

  @DeleteMapping("/{docId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!"owner".equals(doc.role())) {
      throw new ForbiddenException("Only the owner can delete this document.");
    }
    repository.deleteDocument(docId);
  }

  @PostMapping("/{docId}/restore")
  DocumentView restore(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocumentIncludingDeleted(claims.userId(), docId);
    if (!"owner".equals(doc.role())) {
      throw new ForbiddenException("Only the owner can restore this document.");
    }
    repository.restoreDocument(docId);
    redisPublisher.publishDocumentRestored(docId);
    return repository.getDocument(claims.userId(), docId);
  }

  @GetMapping("/{docId}/shares")
  List<ShareView> shares(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    requireSharePermission(authorization, docId);
    var shares = repository.listShares(docId);
    return userInfo.fillShareUsers(shares);
  }

  @PostMapping("/{docId}/shares")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void share(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @RequestBody ShareDocumentRequest req) {
    requireSharePermission(authorization, docId);
    if (!Roles.valid(req.role()) || "owner".equals(req.role())) {
      throw new BadRequestException("Role must be editor or viewer.");
    }
    var userId = userInfo.resolveUserIdByEmail(req.email());
    repository.shareDocument(docId, userId, req.role());
  }

  @DeleteMapping("/{docId}/shares/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeShare(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String userId) {
    requireSharePermission(authorization, docId);
    repository.removeShare(docId, userId);
  }

  @GetMapping("/{docId}/versions")
  List<DocumentVersionSummary> versions(
      @RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    requireDocumentAccess(authorization, docId);
    return repository.listVersions(docId);
  }

  @PostMapping("/{docId}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  DocumentVersionSummary createVersion(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @RequestBody CreateVersionRequest req) {
    var claims = requireDocumentAccess(authorization, docId);
    return repository.createVersion(docId, claims.userId(), req.label());
  }

  @GetMapping("/{docId}/versions/{versionId}")
  DocumentVersion version(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String versionId) {
    requireDocumentAccess(authorization, docId);
    return repository.getVersion(docId, versionId);
  }

  @PostMapping("/{docId}/versions/{versionId}/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void restoreVersion(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String versionId) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canEdit(doc.role())) {
      throw new ForbiddenException("You cannot restore a version for this document.");
    }
    repository.restoreVersion(docId, versionId);
    redisPublisher.publishDocumentRestored(docId);
  }

  @GetMapping("/{docId}/comments")
  List<CommentThread> comments(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    requireDocumentAccess(authorization, docId);
    var comments = repository.listComments(docId);
    return userInfo.fillCommentAuthors(comments);
  }

  @PostMapping("/{docId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  CommentThread createComment(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @RequestBody CreateCommentRequest req) {
    var claims = requireDocumentAccess(authorization, docId);
    if (req.body() == null || req.body().isBlank()) {
      throw new BadRequestException("Comment body is required.");
    }
    var comment = repository.createComment(docId, claims.userId(), req.body().trim());
    var filled = userInfo.fillCommentAuthors(List.of(comment)).get(0);
    redisPublisher.publishCommentEvent(docId, "comment:created", filled);
    return filled;
  }

  @PostMapping("/{docId}/comments/{commentId}/replies")
  @ResponseStatus(HttpStatus.CREATED)
  CommentThread createReply(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String commentId,
      @RequestBody CreateReplyRequest req) {
    var claims = requireDocumentAccess(authorization, docId);
    if (req.body() == null || req.body().isBlank()) {
      throw new BadRequestException("Reply body is required.");
    }
    var comment = repository.addReply(docId, commentId, claims.userId(), req.body().trim());
    var filled = userInfo.fillCommentAuthors(List.of(comment)).get(0);
    redisPublisher.publishCommentEvent(docId, "comment:updated", filled);
    return filled;
  }

  @PatchMapping("/{docId}/comments/{commentId}")
  CommentThread updateComment(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String commentId,
      @RequestBody UpdateCommentRequest req) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canEdit(doc.role())) {
      throw new ForbiddenException("Only editors can update comments.");
    }
    var comment = repository.updateComment(docId, commentId, req);
    var filled = userInfo.fillCommentAuthors(List.of(comment)).get(0);
    redisPublisher.publishCommentEvent(docId, req.resolved() == null ? "comment:updated" : "comment:resolved", filled);
    return filled;
  }

  private void requireSharePermission(String authorization, String docId) {
    var claims = verifier.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canShare(doc.role())) {
      throw new ForbiddenException("Only the owner can manage sharing.");
    }
  }

  private UserClaims requireDocumentAccess(String authorization, String docId) {
    var claims = verifier.claims(authorization);
    repository.getDocument(claims.userId(), docId);
    return claims;
  }
}
