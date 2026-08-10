package com.elatusdev.pokedex.shared.domain;

public class UpstreamTimeoutException extends RuntimeException {
    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
