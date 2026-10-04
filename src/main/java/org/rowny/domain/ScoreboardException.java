package org.rowny.domain;

public final class ScoreboardException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT, MATCH_NOT_FOUND, TEAM_IN_USE
    }

    private final Reason reason;

    public ScoreboardException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
