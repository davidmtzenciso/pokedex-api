package com.elatusdev.pokedex.identity.domain.exception;

// One type for "no such user" and for "wrong password", carrying no detail that separates
// them. Two types, two messages, or two status codes would turn the login endpoint into a
// user-enumeration oracle (java:S5804).
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
