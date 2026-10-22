package com.trustvault.documents;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

import com.trustvault.users.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) { this.documents = documents; }

    @GetMapping
    List<DocumentService.DocumentSummary> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String q, @RequestParam(required = false) String owner,
            @RequestParam(required = false) String category, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DocumentService.InvalidDocumentException("The start date must be on or before the end date.");
        }
        return documents.listOwned(actor.getId(), q, owner, category, from, to);
    }

    @GetMapping("/deleted")
    List<DocumentService.DocumentSummary> listDeleted(@AuthenticationPrincipal AuthenticatedUser actor) {
        return documents.listDeleted(actor.getId());
    }

    @GetMapping("/eligible-viewers")
    List<DocumentService.ViewerOption> eligibleViewers(@AuthenticationPrincipal AuthenticatedUser actor) {
        return documents.eligibleViewers(actor.getId());
    }

    @GetMapping("/{documentId}/grants")
    List<DocumentService.GrantSummary> grants(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID documentId) {
        return documents.listGrants(actor.getId(), documentId);
    }

    @PutMapping("/{documentId}/grants/{userId}")
    DocumentService.GrantSummary grant(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID documentId, @PathVariable UUID userId, @RequestBody GrantRequest request) {
        return documents.grant(actor.getId(), documentId, userId, request == null ? null : request.permission());
    }

    @DeleteMapping("/{documentId}/grants/{userId}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID documentId, @PathVariable UUID userId) {
        documents.revoke(actor.getId(), documentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{documentId}")
    DocumentService.DocumentSummary edit(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID documentId, @RequestBody MetadataRequest request) {
        if (request == null) throw new DocumentService.InvalidDocumentException("Provide a title and category.");
        return documents.editMetadata(actor.getId(), documentId, request.title(), request.category());
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID documentId) {
        documents.delete(actor.getId(), documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{documentId}/restore")
    DocumentService.DocumentSummary restore(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID documentId) {
        return documents.restore(actor.getId(), documentId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentService.DocumentSummary> upload(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam String title, @RequestParam String category, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.upload(actor.getId(), title, category, file));
    }

    @GetMapping("/{documentId}/download")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID documentId) {
        var document = documents.download(actor.getId(), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .body(document.bytes());
    }

    @ExceptionHandler(DocumentService.InvalidDocumentException.class)
    ResponseEntity<Map<String, String>> invalid(DocumentService.InvalidDocumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(DocumentService.NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found."));
    }

    @ExceptionHandler(DocumentService.StorageFailureException.class)
    ResponseEntity<Map<String, String>> storageFailure(DocumentService.StorageFailureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", exception.getMessage()));
    }

    record GrantRequest(com.trustvault.documents.GrantPermission permission) {}
    record MetadataRequest(String title, String category) {}
}
