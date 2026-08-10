package com.elatusdev.pokedex.identity.domain.exception;

public class UserAlreadyExistsException extends RuntimeException {

    private final transient String field;

    public UserAlreadyExistsException(String field) {
        super("A user with that " + field + " already exists");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
