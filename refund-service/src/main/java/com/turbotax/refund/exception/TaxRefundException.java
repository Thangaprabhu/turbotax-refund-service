package com.turbotax.refund.exception;

import org.springframework.http.HttpStatus;

public class TaxRefundException extends RuntimeException {

    private final HttpStatus status;

    private TaxRefundException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static TaxRefundException notFound(String message) {
        return new TaxRefundException(message, HttpStatus.NOT_FOUND);
    }

    public static TaxRefundException conflict(String message) {
        return new TaxRefundException(message, HttpStatus.CONFLICT);
    }

    public static TaxRefundException unauthorized(String message) {
        return new TaxRefundException(message, HttpStatus.UNAUTHORIZED);
    }

    public static TaxRefundException forbidden(String message) {
        return new TaxRefundException(message, HttpStatus.FORBIDDEN);
    }

    public static TaxRefundException badRequest(String message) {
        return new TaxRefundException(message, HttpStatus.BAD_REQUEST);
    }
}
