package com.elatusdev.pokedex.domain.exception;

public class UpstreamTimeoutException extends RuntimeException {
    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
