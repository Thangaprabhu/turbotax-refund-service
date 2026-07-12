package com.turbotax.refund.unit;

import com.turbotax.refund.domain.dto.request.LoginRequest;
import com.turbotax.refund.domain.dto.request.RegisterRequest;
import com.turbotax.refund.domain.entity.User;
import com.turbotax.refund.domain.enums.AccountType;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.repository.UserRepository;
import com.turbotax.refund.security.JwtService;
import com.turbotax.refund.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setup() {
        authService = new AuthService(userRepository, jwtService, passwordEncoder);
        ReflectionTestUtils.setField(authService, "expiryMinutes", 15L);
    }

    @Test
    void register_shouldCreateUserAndReturnToken() {
        var request = new RegisterRequest("new@example.com", "password123", AccountType.CPA);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(UUID.class), org.mockito.ArgumentMatchers.eq("new@example.com"), org.mockito.ArgumentMatchers.eq("USER")))
            .thenReturn("jwt-token");

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.accountType()).isEqualTo(AccountType.CPA);
    }

    @Test
    void register_shouldThrowConflict_whenEmailAlreadyRegistered() {
        var request = new RegisterRequest("existing@example.com", "password123", AccountType.INDIVIDUAL);
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    void login_shouldReturnToken_whenCredentialsAreValid() {
        var request = new LoginRequest("user@example.com", "password123");
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setAccountType(AccountType.INDIVIDUAL);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), "user@example.com", "USER")).thenReturn("jwt-token");

        var response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.accountType()).isEqualTo(AccountType.INDIVIDUAL);
    }

    @Test
    void login_shouldMatchRegardlessOfEmailCasing() {
        var request = new LoginRequest("MixedCase@Example.com", "password123");
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("MixedCase@Example.com");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmailIgnoreCase("MixedCase@Example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token");

        assertThat(authService.login(request).accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_shouldThrowUnauthorized_whenUserNotFound() {
        var request = new LoginRequest("missing@example.com", "password123");
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_shouldThrowUnauthorized_whenAccountDisabled() {
        var request = new LoginRequest("user@example.com", "password123");
        var user = new User();
        user.setActive(false);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("disabled");
    }

    @Test
    void login_shouldThrowUnauthorized_whenPasswordDoesNotMatch() {
        var request = new LoginRequest("user@example.com", "wrong-password");
        var user = new User();
        user.setPasswordHash("hashed");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("Invalid credentials");
    }
}
