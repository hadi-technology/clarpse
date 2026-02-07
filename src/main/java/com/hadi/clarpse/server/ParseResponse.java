package com.hadi.clarpse.server;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.ArrayList;
import java.util.List;

public final class ParseResponse {

    private String language;
    private OOPSourceCodeModel model;
    private List<FailureResponse> failures;
    private long durationMs;

    public ParseResponse() {
    }

    public ParseResponse(final String language, final OOPSourceCodeModel model,
                         final List<FailureResponse> failures, final long durationMs) {
        this.language = language;
        this.model = model;
        if (failures == null) {
            this.failures = new ArrayList<>();
        } else {
            this.failures = failures;
        }
        this.durationMs = durationMs;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public OOPSourceCodeModel getModel() {
        return model;
    }

    public void setModel(final OOPSourceCodeModel model) {
        this.model = model;
    }

    public List<FailureResponse> getFailures() {
        return failures;
    }

    public void setFailures(final List<FailureResponse> failures) {
        if (failures == null) {
            this.failures = new ArrayList<>();
        } else {
            this.failures = failures;
        }
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(final long durationMs) {
        this.durationMs = durationMs;
    }
}
