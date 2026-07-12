package com.turbotax.auth.service;

import com.turbotax.auth.domain.dto.request.LoginRequest;
import com.turbotax.auth.domain.dto.request.RegisterRequest;
import com.turbotax.auth.domain.dto.response.AuthResponse;
import com.turbotax.auth.domain.entity.User;
import com.turbotax.auth.exception.TaxRefundException;
import com.turbotax.auth.repository.UserRepository;
import com.turbotax.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiry-minutes:15}")
    private long expiryMinutes;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw TaxRefundException.conflict("Email already registered: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAccountType(request.accountType());
        User saved = userRepository.save(user);

        log.info("User registered: {} accountType={}", saved.getId(), saved.getAccountType());
        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), "USER");
        return AuthResponse.of(token, expiryMinutes * 60, saved.getAccountType());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> TaxRefundException.unauthorized("Invalid credentials"));

        if (!user.isActive()) {
            throw TaxRefundException.unauthorized("Account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw TaxRefundException.unauthorized("Invalid credentials");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail(), "USER");
        return AuthResponse.of(token, expiryMinutes * 60, user.getAccountType());
    }
}
