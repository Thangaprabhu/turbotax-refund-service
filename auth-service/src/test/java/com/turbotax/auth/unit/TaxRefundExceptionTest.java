package com.turbotax.auth.unit;

import com.turbotax.auth.exception.TaxRefundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TaxRefundExceptionTest {

    @Test
    void notFound_shouldCarry404() {
        var ex = TaxRefundException.notFound("missing");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("missing");
    }

    @Test
    void conflict_shouldCarry409() {
        var ex = TaxRefundException.conflict("dup");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unauthorized_shouldCarry401() {
        var ex = TaxRefundException.unauthorized("nope");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forbidden_shouldCarry403() {
        var ex = TaxRefundException.forbidden("denied");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void badRequest_shouldCarry400() {
        var ex = TaxRefundException.badRequest("bad");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
