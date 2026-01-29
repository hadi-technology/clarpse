package com.hadi.clarpse;

public class TrimmedString {

    private String untrimmedString;
    private String trimValue;

    public TrimmedString(String untrimmedString, String trimValue) {
        this.untrimmedString = untrimmedString;
        this.trimValue = trimValue;
    }

    public String value() throws Exception {
        if (untrimmedString == null || trimValue == null) {
            throw new Exception("Invalid Input found!");
        }
        String result = untrimmedString;

        while (result.startsWith(trimValue)) {
            result = result.substring(trimValue.length());
        }

        while (result.endsWith(trimValue)) {
            result = result.substring(0, result.length() - (trimValue.length()));
        }
        return result;
    }
}
