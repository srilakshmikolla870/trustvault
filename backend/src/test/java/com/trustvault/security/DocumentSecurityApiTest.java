package com.trustvault.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Optional;
import java.util.UUID;

import com.trustvault.audit.AdminActivityService;
import com.trustvault.audit.AuditEventWriter;
import com.trustvault.documents.DocumentEntity;
import com.trustvault.documents.DocumentGrantEntity;
import com.trustvault.documents.DocumentGrantRepository;
import com.trustvault.documents.DocumentRepository;
import com.trustvault.documents.DocumentService;
import com.trustvault.documents.PrivateDocumentStorage;
import com.trustvault.users.AuthenticatedUser;
import com.trustvault.users.UserAdminService;
import com.trustvault.users.UserEntity;
import com.trustvault.users.UserRepository;
import com.trustvault.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest(controllers = {
        com.trustvault.documents.DocumentController.class,
        com.trustvault.users.UserAdminController.class,
        com.trustvault.audit.AdminActivityController.class
})
@Import({SecurityConfiguration.class, DocumentService.class})
class DocumentSecurityApiTest {
    @Autowired private MockMvc mvc;

    @MockitoBean private UserRepository users;
    @MockitoBean private DocumentRepository documents;
    @MockitoBean private DocumentGrantRepository grants;
    @MockitoBean private PrivateDocumentStorage storage;
    @MockitoBean private AuditEventWriter audit;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private UserAdminService userAdminService;
    @MockitoBean private AdminActivityService adminActivityService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final String storageKey = UUID.randomUUID() + ".pdf";
    private DocumentEntity document;
    private UserEntity viewer;

    @BeforeEach
    void setUp() {
        viewer = user(viewerId, UserRole.VIEWER, true);
        document = new DocumentEntity(documentId, ownerId, "Private plan", "Finance", "plan.pdf",
                "application/pdf", 16, "0".repeat(64), storageKey);
        when(users.findById(viewerId)).thenReturn(Optional.of(viewer));
    }

    @Test
    void anotherOwnerCannotDownloadDocumentThroughHttp() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        UserEntity otherOwner = user(otherOwnerId, UserRole.OWNER, true);
        when(users.findById(otherOwnerId)).thenReturn(Optional.of(otherOwner));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));

        mvc.perform(get("/api/documents/{id}/download", documentId)
                        .with(as(otherOwner)))
                .andExpect(status().isNotFound());

        verify(storage, never()).read(any());
        verify(audit).recordIndependentDocumentEvent(otherOwnerId, documentId,
                "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void readOnlyViewerCannotEditMetadataThroughHttp() throws Exception {
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.of(
                new DocumentGrantEntity(UUID.randomUUID(), documentId, viewerId, com.trustvault.documents.GrantPermission.READ, ownerId)));

        mvc.perform(patch("/api/documents/{id}", documentId)
                        .with(as(viewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Changed\",\"category\":\"Changed\"}"))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals("Private plan", document.getTitle());
        verify(documents, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_EDIT", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void viewerCannotCreateOrRevokeAnotherOwnersGrantThroughHttp() throws Exception {
        UUID targetViewerId = UUID.randomUUID();

        mvc.perform(put("/api/documents/{documentId}/grants/{userId}", documentId, targetViewerId)
                        .with(as(viewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"READ\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/documents/{documentId}/grants/{userId}", documentId, targetViewerId)
                        .with(as(viewer)).with(csrf()))
                .andExpect(status().isNotFound());

        verify(grants, never()).save(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_GRANT", "DENIED", "OWNER_REQUIRED");
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_REVOKE", "DENIED", "OWNER_REQUIRED");
    }

    @Test
    void revokedViewerCannotDownloadThroughHttpAndAuditRecordsDenial() throws Exception {
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(grants.findCurrentGrant(documentId, viewerId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/documents/{id}/download", documentId)
                        .with(as(viewer)))
                .andExpect(status().isNotFound());

        verify(storage, never()).read(any());
        verify(audit).recordIndependentDocumentEvent(viewerId, documentId,
                "DOCUMENT_DOWNLOAD", "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND");
    }

    @Test
    void ownerDownloadSucceedsAndAuditRecordsAllowedAction() throws Exception {
        UserEntity owner = user(ownerId, UserRole.OWNER, true);
        byte[] pdf = "%PDF-1.7 fixture".getBytes();
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(documents.findByIdAndStatus(documentId, "ACTIVE")).thenReturn(Optional.of(document));
        when(storage.read(storageKey)).thenReturn(pdf);

        mvc.perform(get("/api/documents/{id}/download", documentId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf));

        verify(audit).recordDocumentEvent(ownerId, documentId,
                "DOCUMENT_DOWNLOAD", "ALLOWED", "DOWNLOAD_SUCCEEDED");
    }

    @Test
    void inactiveAccountSessionIsRejectedAndAuditedBeforeControllerRuns() throws Exception {
        UserEntity inactiveViewer = user(viewerId, UserRole.VIEWER, false);
        when(users.findById(viewerId)).thenReturn(Optional.of(inactiveViewer));

        mvc.perform(get("/api/documents" ).with(as(inactiveViewer)))
                .andExpect(status().isUnauthorized());

        verify(audit).recordInactiveAccountRequest(viewerId);
        verifyNoInteractions(documents, grants, storage);
    }

    @Test
    void missingCsrfTokenRejectsUploadAndCreatesSafeAuditEvent() throws Exception {
        UserEntity owner = user(ownerId, UserRole.OWNER, true);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "%PDF-1.7".getBytes());

        mvc.perform(multipart("/api/documents")
                        .file(file).param("title", "Sample").param("category", "Demo")
                        .with(as(owner)))
                .andExpect(status().isForbidden());

        verify(audit).recordDeniedCsrfRequest(ownerId);
        verifyNoInteractions(documents, grants, storage);
    }

    @Test
    void viewerCannotReadAdministratorActivityAndAttemptIsAudited() throws Exception {
        mvc.perform(get("/api/admin/activity").with(as(viewer)))
                .andExpect(status().isForbidden());

        verify(audit).recordDeniedAdminAccess(viewerId, "INSUFFICIENT_ROLE");
        verifyNoInteractions(adminActivityService);
    }

    @Test
    void administratorCanReadSanitizedActivityEntries() throws Exception {
        UUID adminId = UUID.randomUUID();
        UserEntity admin = user(adminId, UserRole.ADMINISTRATOR, true);
        when(users.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminActivityService.latest(50)).thenReturn(java.util.List.of(
                new AdminActivityService.ActivityEntry(UUID.randomUUID(), java.time.Instant.parse("2026-10-22T10:00:00Z"),
                        "TrustVault Admin", "admin@example.test", "DOCUMENT_DOWNLOAD", "DOCUMENT", documentId,
                        "DENIED", "NOT_AUTHORIZED_OR_NOT_FOUND")));

        mvc.perform(get("/api/admin/activity").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("DOCUMENT_DOWNLOAD"))
                .andExpect(jsonPath("$[0].outcome").value("DENIED"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$[0].content").doesNotExist());

        verify(adminActivityService).latest(50);
    }

    private static UserEntity user(UUID id, UserRole role, boolean active) {
        return new UserEntity(id, role.name().toLowerCase() + "@example.test", role.name(), "password-hash", role, active);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UserEntity entity) {
        AuthenticatedUser principal = new AuthenticatedUser(entity, false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }
}
