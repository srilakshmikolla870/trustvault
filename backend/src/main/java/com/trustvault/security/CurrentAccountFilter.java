package com.trustvault.security;

import java.io.IOException;

import com.trustvault.audit.AuditEventWriter;
import com.trustvault.users.AuthenticatedUser;
import com.trustvault.users.UserEntity;
import com.trustvault.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CurrentAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final AuditEventWriter audit;

    public CurrentAccountFilter(UserRepository users, AuditEventWriter audit) {
        this.users = users;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser priorUser) {
            UserEntity current = users.findById(priorUser.getId()).orElse(null);
            if (current == null || !current.isActive()) {
                try {
                    audit.recordInactiveAccountRequest(priorUser.getId());
                } catch (RuntimeException ignored) {
                    // The request remains denied even if the audit store is unavailable.
                }
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Authentication required\"}");
                return;
            }

            AuthenticatedUser refreshedUser = new AuthenticatedUser(current, false);
            var refreshed = UsernamePasswordAuthenticationToken.authenticated(
                    refreshedUser, authentication.getCredentials(), refreshedUser.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshed);
        }
        chain.doFilter(request, response);
    }
}
