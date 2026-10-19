package com.trustvault.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    List<DocumentEntity> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, String status);
    Optional<DocumentEntity> findByIdAndStatus(UUID id, String status);

    @Query("select d from DocumentEntity d where d.status = 'ACTIVE' and " +
            "(d.ownerId = :userId or exists (select g.id from DocumentGrantEntity g " +
            "where g.documentId = d.id and g.userId = :userId and g.revokedAt is null " +
            "and (g.expiresAt is null or g.expiresAt > CURRENT_TIMESTAMP))) order by d.createdAt desc")
    List<DocumentEntity> findAllVisibleTo(@Param("userId") UUID userId);
}
