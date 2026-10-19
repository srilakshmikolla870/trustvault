package com.trustvault.documents;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

import com.trustvault.audit.AuditEventWriter;
import com.trustvault.users.UserEntity;
import com.trustvault.users.UserRepository;
import com.trustvault.users.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final DocumentGrantRepository grants;
    private final PrivateDocumentStorage storage;
    private final AuditEventWriter audit;

    public DocumentService(DocumentRepository documents, UserRepository users, DocumentGrantRepository grants,
            PrivateDocumentStorage storage, AuditEventWriter audit) {
        this.documents = documents;
        this.users = users;
        this.grants = grants;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public DocumentSummary upload(UUID actorId, String title, String category, MultipartFile file) {
        var user = users.findById(actorId).orElseThrow(NotFoundException::new);
        if (user.getRole() != UserRole.OWNER || !user.isActive()) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "DENIED", "OWNER_REQUIRED");
            throw new NotFoundException();
        }
        byte[] bytes = validate(title, category, file, actorId);
        String key;
        try {
            key = storage.store(bytes);
        } catch (IOException exception) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "FAILED", "STORAGE_FAILURE");
            throw new StorageFailureException();
        }
        var entity = new DocumentEntity(UUID.randomUUID(), actorId, title.trim(), category.trim(),
                safeFilename(file.getOriginalFilename()), "application/pdf", bytes.length,
                sha256(bytes), key);
        try {
            var saved = documents.saveAndFlush(entity);
            audit.recordDocumentEvent(actorId, saved.getId(), "DOCUMENT_UPLOAD", "ALLOWED", "UPLOAD_SUCCEEDED");
            return summary(saved);
        } catch (RuntimeException exception) {
            try { storage.delete(key); } catch (IOException ignored) { }
            throw exception;
        }
    }

    @Transactional
    public List<DocumentSummary> listOwned(UUID actorId) {
        var user = users.findById(actorId).orElseThrow(NotFoundException::new);
        if (!user.isActive() || user.getRole() == UserRole.ADMINISTRATOR) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_LIST", "DENIED", "DOCUMENT_ROLE_REQUIRED");
            return List.of();
        }
        audit.recordDocumentEvent(actorId, null, "DOCUMENT_LIST", "ALLOWED", "AUTHORIZED_LIST_SUCCEEDED");
        return documents.findAllVisibleTo(actorId).stream().map(document -> {
            boolean owner = document.getOwnerId().equals(actorId);
            var grant = owner ? null : grants.findCurrentGrant(document.getId(), actorId).orElse(null);
            return summary(document, owner, owner || (grant != null && grant.getPermission() == GrantPermission.EDIT));
        }).toList();
    }

    @Transactional
    public DownloadedDocument download(UUID actorId, UUID documentId) {
        var user = users.findById(actorId).orElseThrow(NotFoundException::new);
        var document = documents.findByIdAndStatus(documentId, "ACTIVE").orElse(null);
        var grant = document == null ? null : grants.findCurrentGrant(documentId, actorId).orElse(null);
        boolean owner = document != null && document.getOwnerId().equals(actorId);
        if (!user.isActive() || user.getRole() == UserRole.ADMINISTRATOR || document == null
                || (!owner && (user.getRole() != UserRole.VIEWER || grant == null))) {
            audit.recordIndependentDocumentEvent(actorId, documentId, "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
            throw new NotFoundException();
        }
        try {
            byte[] bytes = storage.read(document.getStorageKey());
            audit.recordDocumentEvent(actorId, documentId, "DOCUMENT_DOWNLOAD", "ALLOWED", "DOWNLOAD_SUCCEEDED");
            return new DownloadedDocument(document.getOriginalFilename(), bytes);
        } catch (IOException exception) {
            audit.recordIndependentDocumentEvent(actorId, documentId, "DOCUMENT_DOWNLOAD", "FAILED", "STORAGE_FAILURE");
            throw new StorageFailureException();
        }
    }

    @Transactional
    public List<ViewerOption> eligibleViewers(UUID actorId) {
        requireOwner(actorId, null, "GRANT_LIST", "OWNER_REQUIRED");
        return users.findAllByRoleAndActiveTrueOrderByDisplayNameAsc(UserRole.VIEWER).stream()
                .map(u -> new ViewerOption(u.getId(), u.getDisplayName(), u.getEmail())).toList();
    }

    @Transactional
    public List<GrantSummary> listGrants(UUID actorId, UUID documentId) {
        requireOwnedDocument(actorId, documentId, "GRANT_LIST");
        List<GrantSummary> result = grants.findAllByDocumentIdAndRevokedAtIsNullOrderByGrantedAtDesc(documentId).stream()
                .filter(g -> g.getExpiresAt() == null || g.getExpiresAt().isAfter(Instant.now()))
                .map(g -> users.findById(g.getUserId()).filter(UserEntity::isActive)
                        .map(u -> new GrantSummary(u.getId(), u.getDisplayName(), u.getEmail(), g.getPermission()))
                        .orElse(null)).filter(java.util.Objects::nonNull).toList();
        return result;
    }

    @Transactional
    public GrantSummary grant(UUID actorId, UUID documentId, UUID userId, GrantPermission permission) {
        if (permission == null) throw new InvalidDocumentException("Choose READ or EDIT access.");
        requireOwnedDocument(actorId, documentId, "DOCUMENT_GRANT");
        var target = users.findById(userId).filter(UserEntity::isActive)
                .filter(u -> u.getRole() == UserRole.VIEWER).orElseThrow(NotFoundException::new);
        var grant = grants.findByDocumentIdAndUserIdAndRevokedAtIsNull(documentId, userId).orElse(null);
        if (grant == null) {
            grant = grants.save(new DocumentGrantEntity(UUID.randomUUID(), documentId, userId, permission, actorId));
        } else {
            grant.renewWithPermission(permission);
            grants.save(grant);
        }
        audit.recordDocumentEvent(actorId, documentId, "DOCUMENT_GRANT", "ALLOWED", "GRANT_CREATED_OR_CHANGED");
        return new GrantSummary(target.getId(), target.getDisplayName(), target.getEmail(), permission);
    }

    @Transactional
    public void revoke(UUID actorId, UUID documentId, UUID userId) {
        requireOwnedDocument(actorId, documentId, "DOCUMENT_REVOKE");
        var grant = grants.findByDocumentIdAndUserIdAndRevokedAtIsNull(documentId, userId).orElseThrow(NotFoundException::new);
        grant.revoke();
        grants.save(grant);
        audit.recordDocumentEvent(actorId, documentId, "DOCUMENT_REVOKE", "ALLOWED", "GRANT_REVOKED");
    }

    @Transactional
    public DocumentSummary editMetadata(UUID actorId, UUID documentId, String title, String category) {
        var user = users.findById(actorId).orElseThrow(NotFoundException::new);
        var document = documents.findByIdAndStatus(documentId, "ACTIVE").orElseThrow(NotFoundException::new);
        var grant = grants.findCurrentGrant(documentId, actorId).orElse(null);
        boolean owner = document.getOwnerId().equals(actorId);
        if (!user.isActive() || user.getRole() == UserRole.ADMINISTRATOR || (!owner &&
                (user.getRole() != UserRole.VIEWER || grant == null || grant.getPermission() != GrantPermission.EDIT))) {
            audit.recordIndependentDocumentEvent(actorId, documentId, "DOCUMENT_EDIT", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
            throw new NotFoundException();
        }
        if (title == null || title.isBlank() || title.trim().length() > 200 || category == null || category.isBlank() || category.trim().length() > 80) {
            throw new InvalidDocumentException("Provide a title and category within the allowed length.");
        }
        document.updateMetadata(title.trim(), category.trim());
        var saved = documents.save(document);
        audit.recordDocumentEvent(actorId, documentId, "DOCUMENT_EDIT", "ALLOWED", "METADATA_UPDATED");
        return summary(saved, owner, true);
    }

    private void requireOwner(UUID actorId, UUID documentId, String action, String reason) {
        var actor = users.findById(actorId).orElseThrow(NotFoundException::new);
        if (!actor.isActive() || actor.getRole() != UserRole.OWNER) {
            audit.recordIndependentDocumentEvent(actorId, documentId, action, "DENIED", reason);
            throw new NotFoundException();
        }
    }

    private DocumentEntity requireOwnedDocument(UUID actorId, UUID documentId, String action) {
        requireOwner(actorId, documentId, action, "OWNER_REQUIRED");
        var document = documents.findByIdAndStatus(documentId, "ACTIVE").orElse(null);
        if (document == null || !document.getOwnerId().equals(actorId)) {
            audit.recordIndependentDocumentEvent(actorId, documentId, action, "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
            throw new NotFoundException();
        }
        return document;
    }

    private byte[] validate(String title, String category, MultipartFile file, UUID actorId) {
        if (title == null || title.isBlank() || title.trim().length() > 200
                || category == null || category.isBlank() || category.trim().length() > 80
                || file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "DENIED", "INVALID_METADATA_OR_SIZE");
            throw new InvalidDocumentException("Provide a title, category, and a non-empty PDF no larger than 10 MB.");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || contentType == null
                || !contentType.equalsIgnoreCase("application/pdf")) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "DENIED", "FILE_TYPE_REJECTED");
            throw new InvalidDocumentException("Only PDF files are supported.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes.length > MAX_BYTES
                    || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "DENIED", "FILE_SIGNATURE_REJECTED");
                throw new InvalidDocumentException("The uploaded file is not a valid PDF.");
            }
            return bytes;
        } catch (IOException exception) {
            audit.recordIndependentDocumentEvent(actorId, null, "DOCUMENT_UPLOAD", "FAILED", "UPLOAD_READ_FAILURE");
            throw new InvalidDocumentException("The uploaded file could not be read.");
        }
    }

    private static String safeFilename(String filename) {
        if (filename == null) return "document.pdf";
        String basename = filename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        if (basename.isBlank() || basename.length() > 255 || basename.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidDocumentException("The file name is invalid.");
        }
        return basename;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static DocumentSummary summary(DocumentEntity document) {
        return summary(document, true, true);
    }

    private static DocumentSummary summary(DocumentEntity document, boolean canManageAccess, boolean canEdit) {
        return new DocumentSummary(document.getId(), document.getTitle(), document.getCategory(),
                document.getOriginalFilename(), document.getContentType(), document.getSizeBytes(), document.getCreatedAt(), canManageAccess, canEdit);
    }

    public record DocumentSummary(UUID id, String title, String category, String originalFilename,
            String contentType, long sizeBytes, java.time.Instant createdAt, boolean canManageAccess, boolean canEdit) {}
    public record ViewerOption(UUID id, String displayName, String email) {}
    public record GrantSummary(UUID userId, String displayName, String email, GrantPermission permission) {}
    public record DownloadedDocument(String filename, byte[] bytes) {}
    public static class InvalidDocumentException extends RuntimeException { public InvalidDocumentException(String message) { super(message); } }
    public static class NotFoundException extends RuntimeException {}
    public static class StorageFailureException extends RuntimeException { public StorageFailureException() { super("Document storage is temporarily unavailable."); } }
}
