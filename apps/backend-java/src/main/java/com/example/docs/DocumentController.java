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
public class DocumentController {
  private final AppRepository repository;
  private final AuthController auth;
  private final DocumentSocketHandler socketHandler;

  public DocumentController(AppRepository repository, AuthController auth, DocumentSocketHandler socketHandler) {
    this.repository = repository;
    this.auth = auth;
    this.socketHandler = socketHandler;
  }

  @GetMapping
  List<DocumentView> list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "active") String status) {
    var claims = auth.claims(authorization);
    return repository.listDocuments(claims.userId(), query, status);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DocumentView create(
      @RequestHeader("Authorization") String authorization, @RequestBody CreateDocumentRequest req) {
    var claims = auth.claims(authorization);
    var title = req.title() == null || req.title().isBlank() ? "Untitled document" : req.title();
    return repository.createDocument(claims.userId(), title);
  }

  @GetMapping("/{docId}")
  DocumentView get(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    var claims = auth.claims(authorization);
    return repository.getDocument(claims.userId(), docId);
  }

  @PatchMapping("/{docId}")
  DocumentView rename(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @RequestBody RenameDocumentRequest req) {
    var claims = auth.claims(authorization);
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
    var claims = auth.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!"owner".equals(doc.role())) {
      throw new ForbiddenException("Only the owner can delete this document.");
    }
    repository.deleteDocument(docId);
  }

  @PostMapping("/{docId}/restore")
  DocumentView restore(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    var claims = auth.claims(authorization);
    var doc = repository.getDocumentIncludingDeleted(claims.userId(), docId);
    if (!"owner".equals(doc.role())) {
      throw new ForbiddenException("Only the owner can restore this document.");
    }
    repository.restoreDocument(docId);
    return repository.getDocument(claims.userId(), docId);
  }

  @GetMapping("/{docId}/shares")
  List<ShareView> shares(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    requireSharePermission(authorization, docId);
    return repository.listShares(docId);
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
    repository.shareDocument(docId, req);
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
    var claims = auth.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canEdit(doc.role())) {
      throw new ForbiddenException("You cannot restore a version for this document.");
    }
    repository.restoreVersion(docId, versionId);
  }

  @GetMapping("/{docId}/comments")
  List<CommentThread> comments(@RequestHeader("Authorization") String authorization, @PathVariable String docId) {
    requireDocumentAccess(authorization, docId);
    return repository.listComments(docId);
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
    socketHandler.broadcastCommentEvent(docId, "comment:created", comment);
    return comment;
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
    socketHandler.broadcastCommentEvent(docId, "comment:updated", comment);
    return comment;
  }

  @PatchMapping("/{docId}/comments/{commentId}")
  CommentThread updateComment(
      @RequestHeader("Authorization") String authorization,
      @PathVariable String docId,
      @PathVariable String commentId,
      @RequestBody UpdateCommentRequest req) {
    var claims = auth.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canEdit(doc.role())) {
      throw new ForbiddenException("Only editors can update comments.");
    }
    var comment = repository.updateComment(docId, commentId, req);
    socketHandler.broadcastCommentEvent(docId, req.resolved() == null ? "comment:updated" : "comment:resolved", comment);
    return comment;
  }

  private void requireSharePermission(String authorization, String docId) {
    var claims = auth.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canShare(doc.role())) {
      throw new ForbiddenException("Only the owner can manage sharing.");
    }
  }

  private UserClaims requireDocumentAccess(String authorization, String docId) {
    var claims = auth.claims(authorization);
    repository.getDocument(claims.userId(), docId);
    return claims;
  }
}
