package com.trustvault.users;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.trustvault.audit.AuditEventWriter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventWriter audit;

    public UserAdminService(UserRepository users, PasswordEncoder passwordEncoder, AuditEventWriter audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserSummary createUser(UUID actorId, CreateUser command) {
        String email = command.email() == null ? "" : command.email().strip().toLowerCase(Locale.ROOT);
        String displayName = command.displayName() == null ? "" : command.displayName().strip();
        String password = command.password() == null ? "" : command.password();

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 320) {
            throw new InvalidUserException("Enter a valid email address.");
        }
        if (displayName.isBlank() || displayName.length() > 120) {
            throw new InvalidUserException("Display name must be between 1 and 120 characters.");
        }
        if (command.role() != UserRole.OWNER && command.role() != UserRole.VIEWER) {
            throw new InvalidUserException("Only owner or viewer accounts can be created here.");
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 12 || passwordBytes > 72) {
            throw new InvalidUserException("Password must be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
        if (users.existsByEmail(email)) throw new DuplicateEmailException();

        UserEntity user = new UserEntity(UUID.randomUUID(), email, displayName,
                passwordEncoder.encode(password), command.role(), true);
        UserEntity saved = users.save(user);
        audit.recordUserManagement(actorId, saved.getId(), "ADMIN_USER_CREATE", "USER_CREATED");
        return UserSummary.from(saved);
    }

    @Transactional
    public UserSummary setActive(UUID actorId, UUID targetId, boolean active) {
        UserEntity target = users.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (actorId.equals(targetId)) throw new InvalidUserException("You cannot change your own account status.");
        if (target.getRole() == UserRole.ADMINISTRATOR) {
            throw new InvalidUserException("Administrator account status cannot be changed here.");
        }
        target.setActive(active);
        audit.recordUserManagement(actorId, targetId, "ADMIN_USER_STATUS", active ? "USER_ACTIVATED" : "USER_DEACTIVATED");
        return UserSummary.from(target);
    }

    public record CreateUser(String email, String displayName, UserRole role, String password) {}
    public record UserSummary(UUID id, String email, String displayName, UserRole role, boolean active) {
        static UserSummary from(UserEntity user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isActive());
        }
    }

    public static class InvalidUserException extends RuntimeException {
        public InvalidUserException(String message) { super(message); }
    }
    public static class DuplicateEmailException extends RuntimeException {}
    public static class UserNotFoundException extends RuntimeException {}
}
