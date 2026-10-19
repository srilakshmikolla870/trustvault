package com.trustvault.documents;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_grants")
public class DocumentGrantEntity {
    @Id private UUID id;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private GrantPermission permission;
    @Column(name = "granted_by", nullable = false) private UUID grantedBy;
    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false) private Instant grantedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected DocumentGrantEntity() {}

    public DocumentGrantEntity(UUID id, UUID documentId, UUID userId, GrantPermission permission, UUID grantedBy) {
        this.id = id;
        this.documentId = documentId;
        this.userId = userId;
        this.permission = permission;
        this.grantedBy = grantedBy;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public UUID getUserId() { return userId; }
    public GrantPermission getPermission() { return permission; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setPermission(GrantPermission permission) { this.permission = permission; }
    public void renewWithPermission(GrantPermission permission) {
        this.permission = permission;
        this.expiresAt = null;
        this.revokedAt = null;
    }
    public void revoke() { this.revokedAt = Instant.now(); }
}
