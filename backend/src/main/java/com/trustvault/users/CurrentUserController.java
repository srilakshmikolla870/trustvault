package com.trustvault.users;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
    @GetMapping("/api/auth/me")
    CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    public record CurrentUserResponse(UUID id, String email, String displayName, UserRole role) {}
}
