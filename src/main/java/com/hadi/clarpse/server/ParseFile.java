package com.hadi.clarpse.server;

/**
 * Request payload entry that carries a file path and file content.
 */
public final class ParseFile {

    private String path;
    private String content;

    public ParseFile() {
    }

    public ParseFile(final String path, final String content) {
        this.path = path;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }
}
