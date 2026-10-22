package com.trustvault.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import com.trustvault.audit.AuditEventWriter;
import com.trustvault.users.UserEntity;
import com.trustvault.users.UserRepository;
import com.trustvault.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock private DocumentRepository documents;
    @Mock private DocumentGrantRepository grants;
    @Mock private UserRepository users;
    @Mock private PrivateDocumentStorage storage;
    @Mock private AuditEventWriter audit;
    private DocumentService service;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, users, grants, storage, audit);
        ownerId = UUID.randomUUID();
    }

    private void stubOwner() {
        when(users.findById(ownerId)).thenReturn(Optional.of(
                new UserEntity(ownerId, "owner@example.test", "Owner", "hash", UserRole.OWNER, true)));
    }

    @Test
    void uploadRejectsPdfExtensionWithNonPdfSignature() throws Exception {
        stubOwner();
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "not a pdf".getBytes());
        assertThrows(DocumentService.InvalidDocumentException.class,
                () -> service.upload(ownerId, "Report", "Legal", file));
        verify(storage, never()).store(any());
        verify(audit).recordIndependentDocumentEvent(ownerId, null, "DOCUMENT_UPLOAD", "DENIED", "FILE_SIGNATURE_REJECTED");
    }

    @Test
    void uploadStoresGeneratedKeyAndReturnsOnlySafeMetadata() throws Exception {
        stubOwner();
        byte[] pdf = "%PDF-1.7\nfixture".getBytes();
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", pdf);
        when(storage.store(any(byte[].class))).thenReturn(UUID.randomUUID() + ".pdf");
        when(documents.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.upload(ownerId, "Report", "Legal", file);

        assertEquals("Report", result.title());
        assertEquals("report.pdf", result.originalFilename());
        assertEquals(pdf.length, result.sizeBytes());
        verify(audit).recordDocumentEvent(any(), any(), any(), any(), any());
    }

    @Test
    void downloadDeniesDifferentOwnerBeforeReadingPrivateBytes() throws Exception {
        stubOwner();
        UUID documentId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, otherOwnerId, "Private", "Legal", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));

        assertThrows(DocumentService.NotFoundException.class, () -> service.download(ownerId, documentId));

        verify(storage, never()).read(any());
        verify(audit).recordIndependentDocumentEvent(ownerId, documentId, "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void viewerWithCurrentReadGrantCanDownload() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String storageKey = UUID.randomUUID() + ".pdf";
        byte[] pdf = "%PDF-1.7 fixture".getBytes();
        var document = new DocumentEntity(documentId, ownerId, "Shared", "Work", "shared.pdf",
                "application/pdf", pdf.length, "0".repeat(64), storageKey);
        var grant = new DocumentGrantEntity(UUID.randomUUID(), documentId, viewerId, GrantPermission.READ, ownerId);
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.of(grant));
        when(storage.read(storageKey)).thenReturn(pdf);

        var downloaded = service.download(viewerId, documentId);

        assertEquals("shared.pdf", downloaded.filename());
        assertArrayEquals(pdf, downloaded.bytes());
        verify(audit).recordDocumentEvent(viewerId, documentId, "DOCUMENT_DOWNLOAD", "ALLOWED", "DOWNLOAD_SUCCEEDED");
    }

    @Test
    void viewerWithoutCurrentGrantCannotDownload() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, UUID.randomUUID(), "Shared", "Work", "shared.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.empty());

        assertThrows(DocumentService.NotFoundException.class, () -> service.download(viewerId, documentId));
        verify(storage, never()).read(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void viewerListingUsesVisibilityScopedRepositoryQuery() {
        UUID viewerId = UUID.randomUUID();
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(documents.findAllVisibleTo(viewerId)).thenReturn(java.util.List.of());

        var visibleDocuments = service.listOwned(viewerId);

        assertEquals(java.util.List.of(), visibleDocuments);
        verify(documents).findAllVisibleTo(viewerId);
        verify(audit).recordDocumentEvent(viewerId, null, "DOCUMENT_LIST", "ALLOWED", "AUTHORIZED_LIST_SUCCEEDED");
    }

    @Test
    void documentSearchFiltersOnlyDocumentsReturnedByVisibilityScopedQuery() {
        UUID viewerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID visibleId = UUID.randomUUID();
        UUID privateId = UUID.randomUUID();
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(users.findById(ownerId)).thenReturn(Optional.of(
                new UserEntity(ownerId, "owner@example.test", "Alice Owner", "hash", UserRole.OWNER, true)));
        var visible = new DocumentEntity(visibleId, ownerId, "Quarterly Plan", "Finance", "plan.pdf",
                "application/pdf", 100, "0".repeat(64), UUID.randomUUID() + ".pdf");
        var inaccessible = new DocumentEntity(privateId, UUID.randomUUID(), "Quarterly Private", "Finance", "secret.pdf",
                "application/pdf", 100, "0".repeat(64), UUID.randomUUID() + ".pdf");
        when(documents.findAllVisibleTo(viewerId)).thenReturn(List.of(visible));

        var results = service.listOwned(viewerId, "quarterly", "alice", "finance", null, null);

        assertEquals(1, results.size());
        assertEquals(visibleId, results.get(0).id());
        assertEquals("Alice Owner", results.get(0).ownerDisplayName());
        assertEquals("owner@example.test", results.get(0).ownerEmail());
        verify(documents).findAllVisibleTo(viewerId);
        verify(users, never()).findById(inaccessible.getOwnerId());
    }

    @Test
    void viewerWithoutGrantCannotEditAnotherOwnersMetadata() {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, UUID.randomUUID(), "Private", "Work", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.empty());

        assertThrows(DocumentService.NotFoundException.class,
                () -> service.editMetadata(viewerId, documentId, "Changed", "Changed"));

        assertEquals("Private", document.getTitle());
        verify(documents, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_EDIT", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void readOnlyViewerCannotEditMetadata() {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID actualOwner = UUID.randomUUID();
        var document = new DocumentEntity(documentId, actualOwner, "Private", "Work", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        var readGrant = new DocumentGrantEntity(UUID.randomUUID(), documentId, viewerId, GrantPermission.READ, actualOwner);
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.of(readGrant));

        assertThrows(DocumentService.NotFoundException.class,
                () -> service.editMetadata(viewerId, documentId, "Changed", "Changed"));

        assertEquals("Private", document.getTitle());
        verify(documents, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_EDIT", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void viewerCannotListGrantsOrGrantAccessToAnotherDocument() {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID targetViewerId = UUID.randomUUID();
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));

        assertThrows(DocumentService.NotFoundException.class, () -> service.listGrants(viewerId, documentId));
        assertThrows(DocumentService.NotFoundException.class,
                () -> service.grant(viewerId, documentId, targetViewerId, GrantPermission.READ));

        verify(documents, never()).findByIdAndStatus(any(), any());
        verify(grants, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "GRANT_LIST", "DENIED", "OWNER_REQUIRED");
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "DOCUMENT_GRANT", "DENIED", "OWNER_REQUIRED");
    }

    @Test
    void viewerCannotRevokeAnotherOwnersGrant() {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID targetViewerId = UUID.randomUUID();
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));

        assertThrows(DocumentService.NotFoundException.class, () -> service.revoke(viewerId, documentId, targetViewerId));

        verify(grants, never()).findByDocumentIdAndUserIdAndRevokedAtIsNull(any(), any());
        verify(grants, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "DOCUMENT_REVOKE", "DENIED", "OWNER_REQUIRED");
    }

    @Test
    void deletingOwnedDocumentRevokesEveryCurrentGrantAndAuditsDeletion() {
        stubOwner();
        UUID documentId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, ownerId, "Private", "Work", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        var first = new DocumentGrantEntity(UUID.randomUUID(), documentId, UUID.randomUUID(), GrantPermission.READ, ownerId);
        var second = new DocumentGrantEntity(UUID.randomUUID(), documentId, UUID.randomUUID(), GrantPermission.EDIT, ownerId);
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findAllByDocumentIdAndRevokedAtIsNullOrderByGrantedAtDesc(documentId)).thenReturn(List.of(first, second));

        service.delete(ownerId, documentId);

        assertEquals("DELETED", document.getStatus());
        assertNotNull(document.getDeletedAt());
        assertNotNull(first.getRevokedAt());
        assertNotNull(second.getRevokedAt());
        verify(grants).saveAll(List.of(first, second));
        verify(documents).save(document);
        verify(audit).recordDocumentEvent(ownerId, documentId, "DOCUMENT_DELETE", "ALLOWED", "DOCUMENT_SOFT_DELETED");
    }

    @Test
    void restoringOwnedDeletedDocumentDoesNotRecreateViewerGrants() {
        stubOwner();
        UUID documentId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, ownerId, "Private", "Work", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        document.softDelete(java.time.Instant.now());
        when(documents.findByIdAndStatus(documentId, "DELETED")).thenReturn(Optional.of(document));
        when(documents.save(document)).thenReturn(document);

        var restored = service.restore(ownerId, documentId);

        assertEquals("Private", restored.title());
        assertEquals("ACTIVE", document.getStatus());
        assertNull(document.getDeletedAt());
        verify(documents).save(document);
        verifyNoInteractions(grants);
        verify(audit).recordDocumentEvent(ownerId, documentId, "DOCUMENT_RESTORE", "ALLOWED", "DOCUMENT_RESTORED_NO_GRANTS");
    }

    @Test
    void viewerCannotListOrRestoreDeletedDocuments() {
        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(users.findById(viewerId)).thenReturn(Optional.of(
                new UserEntity(viewerId, "viewer@example.test", "Viewer", "hash", UserRole.VIEWER, true)));

        assertThrows(DocumentService.NotFoundException.class, () -> service.listDeleted(viewerId));
        assertThrows(DocumentService.NotFoundException.class, () -> service.restore(viewerId, documentId));
        assertThrows(DocumentService.NotFoundException.class, () -> service.delete(viewerId, documentId));

        verify(documents, never()).findAllByOwnerIdAndStatusOrderByCreatedAtDesc(any(), any());
        verify(documents, never()).findByIdAndStatus(any(), any());
        verify(documents, never()).save(any());
        verifyNoInteractions(grants);
        verify(audit).recordIndependentDocumentEvent(viewerId, null, "DOCUMENT_LIST_DELETED", "DENIED", "OWNER_REQUIRED");
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "DOCUMENT_RESTORE", "DENIED", "OWNER_REQUIRED");
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId, "DOCUMENT_DELETE", "DENIED", "OWNER_REQUIRED");
    }

    @Test
    void ownerCannotRestoreAnotherOwnersDeletedDocument() {
        stubOwner();
        UUID documentId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, UUID.randomUUID(), "Private", "Work", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        document.softDelete(java.time.Instant.now());
        when(documents.findByIdAndStatus(documentId, "DELETED")).thenReturn(Optional.of(document));

        assertThrows(DocumentService.NotFoundException.class, () -> service.restore(ownerId, documentId));

        verify(documents, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(ownerId, documentId,
                "DOCUMENT_RESTORE", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }
}
