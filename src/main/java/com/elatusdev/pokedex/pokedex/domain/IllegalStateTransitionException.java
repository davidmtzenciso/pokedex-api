package com.elatusdev.pokedex.pokedex.domain;


public class IllegalStateTransitionException extends RuntimeException {

    private final transient ReplicationState from;
    private final transient ReplicationState to;

    public IllegalStateTransitionException(ReplicationState from, ReplicationState to) {
        super("Illegal replication transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public ReplicationState from() {
        return from;
    }

    public ReplicationState to() {
        return to;
    }
}
