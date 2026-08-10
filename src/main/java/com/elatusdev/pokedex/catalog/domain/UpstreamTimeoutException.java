package com.elatusdev.pokedex.catalog.domain;

public class UpstreamTimeoutException extends RuntimeException {
    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
