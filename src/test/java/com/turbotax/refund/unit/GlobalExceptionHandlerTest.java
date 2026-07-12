package com.turbotax.refund.unit;

import com.turbotax.refund.exception.GlobalExceptionHandler;
import com.turbotax.refund.exception.TaxRefundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleTaxRefundException_shouldMapStatusAndMessage() {
        var ex = TaxRefundException.notFound("Taxpayer not found");

        var problem = handler.handleTaxRefundException(ex);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Taxpayer not found");
    }

    @Test
    void handleValidation_shouldReturn400WithFieldErrors() throws Exception {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));

        MethodParameter methodParameter = new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        var problem = handler.handleValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Validation failed");
        assertThat(problem.getProperties()).containsKey("errors");
        @SuppressWarnings("unchecked")
        var errors = (java.util.List<String>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly("email: must not be blank");
    }

    @Test
    void handleUnexpected_shouldReturn500_withoutLeakingDetails() {
        var problem = handler.handleUnexpected(new RuntimeException("some internal secret detail"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getDetail()).doesNotContain("internal secret detail");
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
    }
}
