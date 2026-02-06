package com.hadi.clarpse.server;

public final class ErrorResponse {

    private String error;
    private String message;

    public ErrorResponse() {
    }

    public ErrorResponse(final String error, final String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
