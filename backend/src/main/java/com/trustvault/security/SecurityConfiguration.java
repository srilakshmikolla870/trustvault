package com.trustvault.security;

import java.util.UUID;

import com.trustvault.audit.AuditEventWriter;
import com.trustvault.users.AuthenticatedUser;
import com.trustvault.users.UserRole;
import com.trustvault.users.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DaoAuthenticationProvider provider,
            UserRepository users, AuditEventWriter audit) throws Exception {
        AuthenticationSuccessHandler successHandler = (request, response, authentication) -> {
            try {
                AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
                audit.recordAuthentication(user.getId(), true);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"status\":\"authenticated\"}");
            } catch (RuntimeException auditFailure) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Sign-in is temporarily unavailable\"}");
            }
        };
        AuthenticationFailureHandler failureHandler = (request, response, exception) -> {
            audit.recordAuthentication(null, false);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid email or password\"}");
        };

        http
            .authenticationProvider(provider)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                    "/api/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole(UserRole.ADMINISTRATOR.name())
                .requestMatchers(HttpMethod.POST, "/api/documents").hasRole(UserRole.OWNER.name())
                .requestMatchers("/api/documents/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    try {
                        UUID actorId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                                ? user.getId() : null;
                        audit.recordLogout(actorId);
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    } catch (RuntimeException auditFailure) {
                        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    }
                }))
            .exceptionHandling(errors -> errors
                .defaultAuthenticationEntryPointFor(
                    (request, response, exception) -> {
                        if (request.getRequestURI().startsWith("/api/admin/")) {
                            try {
                                audit.recordDeniedAdminAccess(null, "AUTHENTICATION_REQUIRED");
                            } catch (RuntimeException auditFailure) {
                                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                                return;
                            }
                        }
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    },
                    org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                        .withDefaults().matcher("/api/**"))
                .accessDeniedHandler((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/admin/")) {
                        try {
                            var authentication = org.springframework.security.core.context.SecurityContextHolder
                                    .getContext().getAuthentication();
                            UUID actorId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                                    ? user.getId() : null;
                            audit.recordDeniedAdminAccess(actorId, "INSUFFICIENT_ROLE");
                        } catch (RuntimeException auditFailure) {
                            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                            return;
                        }
                    }
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }))
            .requestCache(cache -> cache.disable())
            .addFilterBefore(new CurrentAccountFilter(users),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }
}
