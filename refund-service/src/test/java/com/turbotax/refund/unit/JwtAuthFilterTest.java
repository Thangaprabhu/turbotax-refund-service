package com.turbotax.refund.unit;

import com.turbotax.refund.security.JwtAuthFilter;
import com.turbotax.refund.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock Claims claims;

    JwtAuthFilter filter;

    @BeforeEach
    void setup() {
        // Constructed here, not as a field initializer -- field initializers run before
        // MockitoExtension injects @Mock fields, so this would otherwise capture a null jwtService.
        filter = new JwtAuthFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldPassThrough_whenNoAuthorizationHeader() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_shouldPassThrough_whenHeaderIsNotBearer() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_shouldSetAuthentication_whenTokenIsValid() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        when(jwtService.validateToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-123");
        when(claims.get("taxpayer_type", String.class)).thenReturn("INDIVIDUAL");

        filter.doFilter(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("user-123");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_INDIVIDUAL");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_shouldReturn401_andSkipChain_whenTokenIsInvalid() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        when(jwtService.validateToken("bad-token")).thenThrow(new RuntimeException("boom"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
