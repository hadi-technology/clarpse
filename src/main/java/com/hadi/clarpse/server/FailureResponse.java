package com.hadi.clarpse.server;

public final class FailureResponse {

    private String path;
    private String message;
    private Integer errorCode;

    public FailureResponse() {
    }

    public FailureResponse(final String path, final String message, final Integer errorCode) {
        this.path = path;
        this.message = message;
        this.errorCode = errorCode;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final Integer errorCode) {
        this.errorCode = errorCode;
    }
}
