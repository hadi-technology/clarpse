package com.hadi.clarpse.server;

import java.util.List;

public final class ParseRequest {

    private String language;
    private List<ParseFile> files;

    public ParseRequest() {
    }

    public ParseRequest(final String language, final List<ParseFile> files) {
        this.language = language;
        this.files = files;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public List<ParseFile> getFiles() {
        return files;
    }

    public void setFiles(final List<ParseFile> files) {
        this.files = files;
    }
}
