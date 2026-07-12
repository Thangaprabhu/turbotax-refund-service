package com.turbotax.auth.repository;

import com.turbotax.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // Email lookups are case-insensitive -- login/register must not depend on
    // whichever casing a given row happened to be stored with.
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
