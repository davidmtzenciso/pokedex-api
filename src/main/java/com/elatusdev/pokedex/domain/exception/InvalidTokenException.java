// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.exception;

// One message for every way a token can be unacceptable — absent, malformed, wrongly signed,
// expired, of the wrong type, or naming a subject that no longer exists. Distinguishing them
// to the caller tells an attacker which guess was closer.
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("The presented token is not valid");
    }
}
