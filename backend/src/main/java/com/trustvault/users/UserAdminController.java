package com.trustvault.users;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    List<UserAdminService.UserSummary> listUsers() {
        return service.listUsers();
    }

    @PostMapping
    ResponseEntity<UserAdminService.UserSummary> createUser(
            @AuthenticationPrincipal AuthenticatedUser actor, @RequestBody CreateUserRequest request) {
        var created = service.createUser(actor.getId(), new UserAdminService.CreateUser(
                request.email(), request.displayName(), request.role(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{userId}/status")
    UserAdminService.UserSummary setStatus(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID userId, @RequestBody StatusRequest request) {
        if (request.active() == null) throw new UserAdminService.InvalidUserException("Active status is required.");
        return service.setActive(actor.getId(), userId, request.active());
    }

    @ExceptionHandler(UserAdminService.InvalidUserException.class)
    ResponseEntity<Map<String, String>> invalidUser(UserAdminService.InvalidUserException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(UserAdminService.DuplicateEmailException.class)
    ResponseEntity<Map<String, String>> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "An account with that email already exists."));
    }

    @ExceptionHandler(UserAdminService.UserNotFoundException.class)
    ResponseEntity<Map<String, String>> userNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
    }

    public record CreateUserRequest(String email, String displayName, UserRole role, String password) {}
    public record StatusRequest(Boolean active) {}
}
