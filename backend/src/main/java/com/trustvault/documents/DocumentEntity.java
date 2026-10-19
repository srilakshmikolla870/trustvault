package com.trustvault.documents;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class DocumentEntity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 80) private String category;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 150) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "checksum_sha256", nullable = false, columnDefinition = "char(64)") private String checksumSha256;
    @Column(name = "storage_key", nullable = false, length = 128, unique = true) private String storageKey;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected DocumentEntity() {}

    public DocumentEntity(UUID id, UUID ownerId, String title, String category, String originalFilename,
            String contentType, long sizeBytes, String checksumSha256, String storageKey) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.category = category;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.storageKey = storageKey;
        this.status = "ACTIVE";
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getStorageKey() { return storageKey; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
