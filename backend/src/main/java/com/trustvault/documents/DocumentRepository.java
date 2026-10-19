package com.trustvault.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    List<DocumentEntity> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, String status);
    Optional<DocumentEntity> findByIdAndStatus(UUID id, String status);
}
