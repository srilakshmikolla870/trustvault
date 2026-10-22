package com.trustvault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.trustvault.audit.AuditEventWriter;
import com.trustvault.users.AuthenticatedUser;
import com.trustvault.users.UserEntity;
import com.trustvault.users.UserRepository;
import com.trustvault.users.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.FilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentAccountFilterTest {
    private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
    private final AuditEventWriter audit = org.mockito.Mockito.mock(AuditEventWriter.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledAccountInvalidatesExistingSessionBeforeRequestContinues() throws Exception {
        UUID id = UUID.randomUUID();
        AuthenticatedUser prior = new AuthenticatedUser(
                new UserEntity(id, "owner@example.test", "Owner", "hash", UserRole.OWNER, true));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(prior, null, prior.getAuthorities()));
        when(users.findById(id)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);
        FilterChain chain = (requestToContinue, responseToContinue) -> continued.set(true);

        new CurrentAccountFilter(users, audit).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());
        assertFalse(continued.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(users).findById(id);
        verify(audit).recordInactiveAccountRequest(id);
    }

    @Test
    void refreshesRoleFromDatabaseOnEveryAuthenticatedRequest() throws Exception {
        UUID id = UUID.randomUUID();
        AuthenticatedUser prior = new AuthenticatedUser(
                new UserEntity(id, "owner@example.test", "Owner", "hash", UserRole.OWNER, true));
        UserEntity updated = new UserEntity(id, "owner@example.test", "Owner", "hash", UserRole.VIEWER, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(prior, null, prior.getAuthorities()));
        when(users.findById(id)).thenReturn(Optional.of(updated));
        AtomicBoolean continued = new AtomicBoolean(false);
        FilterChain chain = (requestToContinue, responseToContinue) -> continued.set(true);

        new CurrentAccountFilter(users, audit).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        AuthenticatedUser refreshed = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(UserRole.VIEWER, refreshed.getRole());
        assertNull(refreshed.getPassword());
        assertEquals("ROLE_VIEWER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        assertTrue(continued.get());
        verify(users).findById(id);
    }
}
