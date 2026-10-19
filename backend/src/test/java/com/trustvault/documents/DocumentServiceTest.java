package com.trustvault.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

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
    @Mock private UserRepository users;
    @Mock private PrivateDocumentStorage storage;
    @Mock private AuditEventWriter audit;
    private DocumentService service;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, users, storage, audit);
        ownerId = UUID.randomUUID();
        when(users.findById(ownerId)).thenReturn(Optional.of(
                new UserEntity(ownerId, "owner@example.test", "Owner", "hash", UserRole.OWNER, true)));
    }

    @Test
    void uploadRejectsPdfExtensionWithNonPdfSignature() throws Exception {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "not a pdf".getBytes());
        assertThrows(DocumentService.InvalidDocumentException.class,
                () -> service.upload(ownerId, "Report", "Legal", file));
        verify(storage, never()).store(any());
        verify(audit).recordIndependentDocumentEvent(ownerId, null, "DOCUMENT_UPLOAD", "DENIED", "FILE_SIGNATURE_REJECTED");
    }

    @Test
    void uploadStoresGeneratedKeyAndReturnsOnlySafeMetadata() throws Exception {
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
        UUID documentId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        var document = new DocumentEntity(documentId, otherOwnerId, "Private", "Legal", "private.pdf",
                "application/pdf", 10, "0".repeat(64), UUID.randomUUID() + ".pdf");
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));

        assertThrows(DocumentService.NotFoundException.class, () -> service.download(ownerId, documentId));

        verify(storage, never()).read(any());
        verify(audit).recordIndependentDocumentEvent(ownerId, documentId, "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }
}
