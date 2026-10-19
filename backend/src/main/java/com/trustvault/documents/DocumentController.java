package com.trustvault.documents;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    List<DocumentService.DocumentSummary> list(@AuthenticationPrincipal AuthenticatedUser actor) {
        return documents.listOwned(actor.getId());
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
}
