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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
  private final AppRepository repository;
  private final AuthController auth;

  public DocumentController(AppRepository repository, AuthController auth) {
    this.repository = repository;
    this.auth = auth;
  }

  @GetMapping
  List<DocumentView> list(@RequestHeader("Authorization") String authorization) {
    var claims = auth.claims(authorization);
    return repository.listDocuments(claims.userId());
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
    repository.renameDocument(docId, req.title());
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

  private void requireSharePermission(String authorization, String docId) {
    var claims = auth.claims(authorization);
    var doc = repository.getDocument(claims.userId(), docId);
    if (!Roles.canShare(doc.role())) {
      throw new ForbiddenException("Only the owner can manage sharing.");
    }
  }
}
