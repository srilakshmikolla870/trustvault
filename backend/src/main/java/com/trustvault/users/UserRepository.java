package com.trustvault.users;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByRole(UserRole role);
    boolean existsByEmail(String email);
    List<UserEntity> findAllByOrderByCreatedAtDesc();
    List<UserEntity> findAllByRoleAndActiveTrueOrderByDisplayNameAsc(UserRole role);
}
