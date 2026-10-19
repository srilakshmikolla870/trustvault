package com.trustvault.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.trustvault.audit.AuditEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditEventWriter audit;
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(users, passwordEncoder, audit);
    }

    @Test
    void createsNormalizedViewerWithEncodedPasswordAndAuditEvent() {
        UUID adminId = UUID.randomUUID();
        when(users.existsByEmail("viewer@example.test")).thenReturn(false);
        when(passwordEncoder.encode("correct-horse-battery-staple")).thenReturn("bcrypt-hash");
        when(users.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAdminService.UserSummary result = service.createUser(adminId,
                new UserAdminService.CreateUser(" Viewer@Example.Test ", " Test Viewer ", UserRole.VIEWER,
                        "correct-horse-battery-staple"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertEquals("viewer@example.test", saved.getValue().getEmail());
        assertEquals("Test Viewer", saved.getValue().getDisplayName());
        assertEquals("bcrypt-hash", saved.getValue().getPasswordHash());
        assertEquals(UserRole.VIEWER, saved.getValue().getRole());
        assertTrue(saved.getValue().isActive());
        assertEquals("viewer@example.test", result.email());
        assertFalse(result.toString().contains("bcrypt-hash"));
        verify(audit).recordUserManagement(adminId, result.id(), "ADMIN_USER_CREATE", "USER_CREATED");
    }

    @Test
    void rejectsAdminRoleAndShortPasswordWithoutWritingUser() {
        assertThrows(UserAdminService.InvalidUserException.class, () -> service.createUser(UUID.randomUUID(),
                new UserAdminService.CreateUser("admin2@example.test", "Admin Two", UserRole.ADMINISTRATOR,
                        "long-enough-password")));
        assertThrows(UserAdminService.InvalidUserException.class, () -> service.createUser(UUID.randomUUID(),
                new UserAdminService.CreateUser("owner@example.test", "Owner", UserRole.OWNER, "short")));
        verify(users, never()).save(any(UserEntity.class));
        verify(audit, never()).recordUserManagement(any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(users.existsByEmail("owner@example.test")).thenReturn(true);
        assertThrows(UserAdminService.DuplicateEmailException.class, () -> service.createUser(UUID.randomUUID(),
                new UserAdminService.CreateUser("OWNER@example.test", "Owner", UserRole.OWNER,
                        "correct-horse-battery-staple")));
        verify(users, never()).save(any(UserEntity.class));
    }

    @Test
    void preventsAdministratorFromDeactivatingTheirOwnAccount() {
        UUID adminId = UUID.randomUUID();
        when(users.findById(adminId)).thenReturn(Optional.of(new UserEntity(adminId, "admin@example.test", "Admin",
                "hash", UserRole.ADMINISTRATOR, true)));
        assertThrows(UserAdminService.InvalidUserException.class, () -> service.setActive(adminId, adminId, false));
    }

    @Test
    void deactivatesOwnerAndWritesAuditEvent() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UserEntity owner = new UserEntity(ownerId, "owner@example.test", "Owner", "hash", UserRole.OWNER, true);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));

        UserAdminService.UserSummary result = service.setActive(adminId, ownerId, false);

        assertFalse(result.active());
        assertFalse(owner.isActive());
        verify(audit).recordUserManagement(adminId, ownerId, "ADMIN_USER_STATUS", "USER_DEACTIVATED");
    }
}
