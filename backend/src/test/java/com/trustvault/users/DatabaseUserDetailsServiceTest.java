package com.trustvault.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {
    @Mock UserRepository users;

    @Test
    void normalizesEmailBeforeLookupAndMapsServerStoredRole() {
        UserEntity entity = new UserEntity(UUID.randomUUID(), "viewer@example.test", "Viewer", "hash",
                UserRole.VIEWER, true);
        when(users.findByEmail("viewer@example.test")).thenReturn(Optional.of(entity));

        AuthenticatedUser result = (AuthenticatedUser) new DatabaseUserDetailsService(users)
                .loadUserByUsername("  VIEWER@Example.Test ");

        assertEquals("viewer@example.test", result.getUsername());
        assertEquals("hash", result.getPassword());
        assertTrue(result.isEnabled());
        assertEquals("ROLE_VIEWER", result.getAuthorities().iterator().next().getAuthority());
        verify(users).findByEmail("viewer@example.test");
    }

    @Test
    void disabledAccountIsNotEnabled() {
        UserEntity entity = new UserEntity(UUID.randomUUID(), "disabled@example.test", "Disabled", "hash",
                UserRole.OWNER, false);
        AuthenticatedUser result = new AuthenticatedUser(entity);
        assertFalse(result.isEnabled());
    }

    @Test
    void erasedCredentialsRemovePasswordHashFromPrincipal() {
        UserEntity entity = new UserEntity(UUID.randomUUID(), "owner@example.test", "Owner", "hash",
                UserRole.OWNER, true);
        AuthenticatedUser result = new AuthenticatedUser(entity);
        result.eraseCredentials();
        org.junit.jupiter.api.Assertions.assertNull(result.getPassword());
    }

    @Test
    void missingAccountUsesGenericAuthenticationLookupError() {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> new DatabaseUserDetailsService(users).loadUserByUsername("missing@example.test"));
    }
}
