package com.turbotax.refund.unit;

import com.turbotax.refund.controller.AuthController;
import com.turbotax.refund.domain.dto.request.LoginRequest;
import com.turbotax.refund.domain.dto.request.RegisterRequest;
import com.turbotax.refund.domain.dto.response.AuthResponse;
import com.turbotax.refund.domain.enums.AccountType;
import com.turbotax.refund.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;

    AuthController controller;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        controller = new AuthController(authService);
    }

    @Test
    void register_shouldDelegateToAuthService() {
        var request = new RegisterRequest("user@example.com", "password123", AccountType.INDIVIDUAL);
        var expected = AuthResponse.of("token", 900L, AccountType.INDIVIDUAL);
        when(authService.register(request)).thenReturn(expected);

        var result = controller.register(request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void login_shouldDelegateToAuthService() {
        var request = new LoginRequest("user@example.com", "password123");
        var expected = AuthResponse.of("token", 900L, AccountType.CPA);
        when(authService.login(request)).thenReturn(expected);

        var result = controller.login(request);

        assertThat(result).isSameAs(expected);
    }
}
