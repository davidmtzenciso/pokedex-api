package com.elatusdev.pokedex.shared.domain.exception;

public class InvalidPaginationException extends RuntimeException {

    private final transient int page;
    private final transient int size;

    public InvalidPaginationException(String message, int page, int size) {
        super(message);
        this.page = page;
        this.size = size;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }
}
