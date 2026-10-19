package com.trustvault.users;

import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdministratorBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdministratorBootstrap(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL:}") String email,
            @Value("${TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (email.isBlank() || password.isBlank() || password.length() < 12 || passwordBytes > 72 || email.length() > 320) {
            throw new IllegalStateException("Bootstrap admin requires a valid email and a password of 12 to 72 UTF-8 bytes");
        }
        if (users.existsByRole(UserRole.ADMINISTRATOR)) return;

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("Bootstrap admin email is invalid");
        }
        users.save(new UserEntity(UUID.randomUUID(), normalizedEmail, "TrustVault Administrator",
                passwordEncoder.encode(password), UserRole.ADMINISTRATOR, true));
    }
}
