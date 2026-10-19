package com.trustvault.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentGrantRepository extends JpaRepository<DocumentGrantEntity, UUID> {
    Optional<DocumentGrantEntity> findByDocumentIdAndUserIdAndRevokedAtIsNull(UUID documentId, UUID userId);
    List<DocumentGrantEntity> findAllByDocumentIdAndRevokedAtIsNullOrderByGrantedAtDesc(UUID documentId);

    @Query("select g from DocumentGrantEntity g where g.documentId = :documentId and g.userId = :userId " +
            "and g.revokedAt is null and (g.expiresAt is null or g.expiresAt > CURRENT_TIMESTAMP)")
    Optional<DocumentGrantEntity> findCurrentGrant(@Param("documentId") UUID documentId, @Param("userId") UUID userId);
}
